package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.config.properties.AppMailProperties;
import bao.gabriel.cognia.config.properties.VerificationCodeProperties;
import bao.gabriel.cognia.security.TurnstileService;
import bao.gabriel.cognia.system.dto.auth.SendCodeResponse;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Email verification codes backed by Redis:
 *
 * <ul>
 *   <li>{@code auth:verify:register:code:{email}} - current code (TTL)</li>
 *   <li>{@code auth:verify:register:rate:{email}} - resend cooldown</li>
 *   <li>{@code auth:verify:register:attempt:{email}} - wrong attempts</li>
 *   <li>{@code auth:verify:register:daily:{email}:{date}} - daily quota</li>
 *   <li>{@code auth:verify:ip:{ip}} - per-IP hourly quota</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final String CODE_KEY = "auth:verify:register:code:";
    private static final String RATE_KEY = "auth:verify:register:rate:";
    private static final String ATTEMPT_KEY = "auth:verify:register:attempt:";
    private static final String DAILY_KEY = "auth:verify:register:daily:";
    private static final String IP_KEY = "auth:verify:ip:";
    private static final int IP_HOURLY_LIMIT = 30;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final VerificationCodeProperties properties;
    private final AppMailProperties mailProperties;
    private final EmailService emailService;
    private final SiteSettingService settingService;
    private final TurnstileService turnstileService;
    private final UserMapper userMapper;

    public SendCodeResponse sendRegisterCode(String rawEmail, String clientIp, String turnstileToken) {
        if (!settingService.isRegistrationEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "注册功能已关闭");
        }
        if (!settingService.isEmailVerificationRequired()) {
            throw BusinessException.badRequest("注册邮箱验证已关闭");
        }
        String email = normalize(rawEmail);
        if (!settingService.isRegistrationEmailAllowed(email)) {
            throw BusinessException.badRequest("该邮箱不在允许注册的范围内");
        }

        // Server-side human verification happens before any email work.
        turnstileService.verify(turnstileToken, clientIp, settingService.isRegisterEmailCaptchaEnabled());

        Long existing = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing != null && existing > 0) {
            throw BusinessException.conflict("该邮箱已被注册，请直接登录");
        }

        // Per-IP hourly quota (best effort, avoids email bombing).
        if (clientIp != null && !clientIp.isBlank()) {
            String ipKey = IP_KEY + clientIp;
            Long ipCount = redis.opsForValue().increment(ipKey);
            if (ipCount != null && ipCount == 1) {
                redis.expire(ipKey, Duration.ofHours(1));
            }
            if (ipCount != null && ipCount > IP_HOURLY_LIMIT) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试");
            }
        }

        // Resend cooldown, atomic through SET NX.
        Boolean acquired = redis.opsForValue().setIfAbsent(RATE_KEY + email, "1", properties.getResendInterval());
        if (!Boolean.TRUE.equals(acquired)) {
            Long ttl = redis.getExpire(RATE_KEY + email);
            long wait = ttl == null || ttl < 0 ? properties.getResendInterval().toSeconds() : ttl;
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "验证码发送过于频繁，请 " + wait + " 秒后再试");
        }

        // Daily quota per email.
        String dailyKey = DAILY_KEY + email + ":" + LocalDate.now();
        Long dailyCount = redis.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            redis.expire(dailyKey, Duration.ofDays(1));
        }
        if (dailyCount != null && dailyCount > properties.getDailyLimit()) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "今日验证码发送次数已达上限");
        }

        String code = generateCode(properties.getLength());
        redis.opsForValue().set(CODE_KEY + email, code, properties.getTtl());
        redis.delete(ATTEMPT_KEY + email);

        try {
            emailService.sendRegisterCode(email, code, properties.getTtl().toMinutes());
        } catch (RuntimeException ex) {
            // Do not keep a code the user never received.
            redis.delete(CODE_KEY + email);
            throw ex;
        }

        String devCode = mailProperties.isExposeCode() ? code : null;
        return new SendCodeResponse("验证码已发送，请注意查收", properties.getTtl().toSeconds(),
                properties.getResendInterval().toSeconds(), devCode);
    }

    /**
     * Verifies and consumes the registration code.
     */
    public void verifyRegisterCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        String codeKey = CODE_KEY + email;
        String stored = redis.opsForValue().get(codeKey);
        if (stored == null) {
            throw BusinessException.badRequest("验证码已过期，请重新获取");
        }

        String attemptKey = ATTEMPT_KEY + email;
        Long attempts = redis.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1) {
            redis.expire(attemptKey, properties.getTtl());
        }
        if (attempts != null && attempts > properties.getMaxAttempts()) {
            redis.delete(codeKey);
            redis.delete(attemptKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取");
        }

        boolean matches = MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            long remaining = Math.max(0, properties.getMaxAttempts() - (attempts == null ? 0 : attempts));
            throw BusinessException.badRequest("验证码不正确，还可尝试 " + remaining + " 次");
        }

        redis.delete(codeKey);
        redis.delete(attemptKey);
    }

    private String generateCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    private String normalize(String email) {
        if (email == null) {
            throw BusinessException.badRequest("邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
