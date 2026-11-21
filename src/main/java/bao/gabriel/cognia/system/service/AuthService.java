package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.config.properties.LoginProperties;
import bao.gabriel.cognia.security.CaptchaService;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.security.TokenPair;
import bao.gabriel.cognia.security.TokenService;
import bao.gabriel.cognia.system.dto.auth.LoginOptionsResponse;
import bao.gabriel.cognia.system.dto.auth.LoginRequest;
import bao.gabriel.cognia.system.dto.auth.LoginCodeSendRequest;
import bao.gabriel.cognia.system.dto.auth.LoginEmailCodeRequest;
import bao.gabriel.cognia.system.dto.auth.LoginTwoFactorRequest;
import bao.gabriel.cognia.system.dto.auth.LoginResponse;
import bao.gabriel.cognia.system.dto.auth.RegisterRequest;
import bao.gabriel.cognia.system.dto.auth.SendCodeRequest;
import bao.gabriel.cognia.system.dto.auth.SendCodeResponse;
import bao.gabriel.cognia.system.dto.auth.AvailabilityResponse;
import bao.gabriel.cognia.system.dto.user.UserCreateRequest;
import bao.gabriel.cognia.system.dto.user.UserResponse;
import bao.gabriel.cognia.system.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Orchestrates registration, login, refresh and logout. Password verification
 * is performed manually so that account lockout and disabled-account checks
 * produce precise business errors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_FAIL_PREFIX = "auth:login:fail:";

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final SiteSettingService settingService;
    private final LegalService legalService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final LoginProperties loginProperties;
    private final TwoFactorService twoFactorService;

    public SendCodeResponse sendRegisterCode(SendCodeRequest request, String clientIp) {
        return verificationCodeService.sendRegisterCode(
                request.email(), clientIp, request.captcha(), request.turnstileToken());
    }

    public AvailabilityResponse checkEmail(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return AvailabilityResponse.fail("邮箱格式不正确");
        }
        if (!settingService.isRegistrationEmailAllowed(value)) {
            return AvailabilityResponse.fail("该邮箱不在允许注册的范围内");
        }
        return userService.isEmailAvailable(value)
                ? AvailabilityResponse.ok()
                : AvailabilityResponse.fail("该邮箱已被注册");
    }

    public AvailabilityResponse checkUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (!value.matches("^[a-zA-Z][a-zA-Z0-9_]{2,31}$")) {
            return AvailabilityResponse.fail("唯一标识需以字母开头，只能包含字母、数字、下划线，长度 3-32");
        }
        if (!userService.isUsernameAvailable(value)) {
            return AvailabilityResponse.fail("该唯一标识已被占用");
        }
        return AvailabilityResponse.ok();
    }

    public java.util.List<String> usernameSuggestions(String base) {
        return userService.suggestUsernames(base);
    }

    public void verifyRegisterCode(String email, String code) {
        if (!settingService.isEmailVerificationRequired()) {
            return;
        }
        verificationCodeService.checkRegisterCode(email, code);
    }

    public LoginOptionsResponse loginOptions(String rawAccount) {
        String account = rawAccount == null ? "" : rawAccount.trim();
        User user = account.isEmpty() ? null : userService.findByAccount(account);
        if (user == null || !user.isEnabled()) {
            return new LoginOptionsResponse(false, false, false);
        }
        boolean twoFactor = settingService.isTwoFactorEnabled() && Boolean.TRUE.equals(user.getTwoFactorEnabled());
        boolean emailCode = settingService.isEmailCodeLoginEnabled() && StringUtils.hasText(user.getEmail());
        return new LoginOptionsResponse(StringUtils.hasText(user.getPassword()), twoFactor, emailCode);
    }

    public SendCodeResponse sendLoginCode(LoginCodeSendRequest request, String clientIp) {
        return verificationCodeService.sendLoginCode(request.account(), clientIp, request.turnstileToken());
    }

    public LoginResponse loginWithEmailCode(LoginEmailCodeRequest request, String clientIp, String userAgent) {
        if (!settingService.isEmailCodeLoginEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "邮箱验证码登录未启用");
        }
        User user = verificationCodeService.verifyLoginCode(request.account(), request.code());
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账户已被禁用，请联系管理员");
        }
        return completeLogin(user, Boolean.TRUE.equals(request.rememberMe()), clientIp, userAgent);
    }

    public UserResponse register(RegisterRequest request) {
        if (!settingService.isRegistrationEnabled()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "注册功能已关闭");
        }
        String username = request.username().trim();
        if (!settingService.isRegistrationEmailAllowed(request.email())) {
            throw BusinessException.badRequest("该邮箱不在允许注册的范围内");
        }
        userService.assertUsernameAvailable(username, null);
        userService.assertEmailAvailable(request.email(), null);

        if (settingService.isEmailVerificationRequired()) {
            if (!StringUtils.hasText(request.code())) {
                throw BusinessException.badRequest("请输入邮箱验证码");
            }
            verificationCodeService.verifyRegisterCode(request.email(), request.code());
        }

        UserResponse user = userService.createUser(new UserCreateRequest(
                username,
                request.nickname(),
                request.email(),
                request.password(),
                null,
                1,
                null
        ));
        // New accounts accept the currently published documents at registration;
        // future revisions will still trigger the re-consent dialog.
        legalService.recordConsentForNewUser(Long.valueOf(user.id()));
        log.info("User registered: {} ({})", user.username(), user.id());
        return user;
    }

    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        // Human verification is checked before credentials / failure counters.
        captchaService.verify(request.captcha(), request.turnstileToken(), clientIp,
                settingService.isLoginCaptchaEnabled());

        String account = request.account().trim();
        String failKey = LOGIN_FAIL_PREFIX + account.toLowerCase(Locale.ROOT);

        assertNotLocked(failKey);

        User user = userService.findByAccount(account);
        if (user != null && !StringUtils.hasText(user.getPassword())) {
            throw BusinessException.badRequest("该账户使用第三方登录，请使用对应登录方式");
        }
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            int failures = recordFailure(failKey);
            int remaining = Math.max(0, loginProperties.getMaxFailures() - failures);
            if (remaining == 0) {
                throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                        "密码错误次数过多，账户已锁定 " + loginProperties.getLockDuration().toMinutes() + " 分钟");
            }
            throw new BusinessException(ResultCode.BAD_CREDENTIALS,
                    "用户名或密码错误，还可尝试 " + remaining + " 次");
        }

        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        redis.delete(failKey);
        return completeLogin(user, Boolean.TRUE.equals(request.rememberMe()), clientIp, userAgent);
    }

    public LoginResponse loginWithTwoFactor(LoginTwoFactorRequest request, String clientIp, String userAgent) {
        captchaService.verify(request.captcha(), request.turnstileToken(), clientIp,
                settingService.isLoginCaptchaEnabled());
        if (!settingService.isTwoFactorEnabled()) {
            throw BusinessException.badRequest("当前站点未开启两步验证登录");
        }
        String account = request.account().trim();
        String failKey = LOGIN_FAIL_PREFIX + account.toLowerCase(Locale.ROOT);
        assertNotLocked(failKey);
        User user = userService.findByAccount(account);
        if (user == null || !Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIALS, "该账户未开启两步验证");
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (!twoFactorService.verify(user, request.code())) {
            int failures = recordFailure(failKey);
            int remaining = Math.max(0, loginProperties.getMaxFailures() - failures);
            if (remaining == 0) {
                throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                        "验证码错误次数过多，账户已锁定 " + loginProperties.getLockDuration().toMinutes() + " 分钟");
            }
            throw new BusinessException(ResultCode.BAD_CREDENTIALS,
                    "两步验证码不正确，还可尝试 " + remaining + " 次");
        }
        redis.delete(failKey);
        return completeLogin(user, Boolean.TRUE.equals(request.rememberMe()), clientIp, userAgent);
    }

    private LoginResponse completeLogin(User user, boolean rememberMe, String clientIp, String userAgent) {
        userService.updateLastLogin(user.getId(), clientIp);
        LoginUser loginUser = userService.buildLoginUser(user);
        TokenPair tokens = tokenService.issue(loginUser, rememberMe, clientIp, userAgent);
        UserResponse profile = userService.getProfile(user.getId());
        log.info("User logged in: {} from {}", user.getUsername(), clientIp);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                tokens.expiresIn(), tokens.refreshExpiresIn(), profile);
    }

    public LoginResponse refresh(String refreshToken, String clientIp, String userAgent) {
        Long userId = tokenService.consumeRefreshToken(refreshToken);
        User user = userService.requireUser(userId);
        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        LoginUser loginUser = userService.buildLoginUser(user);
        TokenPair tokens = tokenService.issue(loginUser, false, clientIp, userAgent);
        UserResponse profile = userService.getProfile(user.getId());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                tokens.expiresIn(), tokens.refreshExpiresIn(), profile);
    }

    public void logout() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser != null) {
            tokenService.logout(loginUser);
            log.info("User logged out: {}", loginUser.getUsername());
        }
        SecurityContextHolder.clearContext();
    }

    public UserResponse me() {
        return userService.getProfile(SecurityUtils.getUserId());
    }

    private void assertNotLocked(String failKey) {
        String value = redis.opsForValue().get(failKey);
        if (value == null) {
            return;
        }
        long failures;
        try {
            failures = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return;
        }
        if (failures < loginProperties.getMaxFailures()) {
            return;
        }
        Long ttl = redis.getExpire(failKey);
        long minutes = ttl == null || ttl <= 0
                ? loginProperties.getLockDuration().toMinutes()
                : Math.max(1, (ttl + 59) / 60);
        throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                "账户已锁定，请 " + minutes + " 分钟后再试");
    }

    private int recordFailure(String failKey) {
        Long failures = redis.opsForValue().increment(failKey);
        if (failures != null && failures == 1) {
            redis.expire(failKey, loginProperties.getLockDuration());
        }
        // Start the full lock window when the threshold is reached, so the
        // account cannot be retried immediately after a slow sequence of tries.
        if (failures != null && failures == loginProperties.getMaxFailures()) {
            redis.expire(failKey, loginProperties.getLockDuration());
        }
        return failures == null ? 1 : failures.intValue();
    }
}
