package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.settings.CaptchaConfig;
import bao.gabriel.cognia.system.dto.settings.CaptchaProvider;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Entry point for human verification. Exactly one provider (Cloudflare
 * Turnstile or GeeTest 4.0) is active, chosen in the console; the verifier is
 * picked from the persisted configuration, never from client input.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final SiteSettingService settingService;
    private final TurnstileVerifier turnstileVerifier;
    private final GeeTestVerifier geeTestVerifier;

    /**
     * Verifies the challenge when the caller's flow requires human
     * verification. No-op when {@code required} is false, so disabled flows
     * never block on a stale or missing provider.
     *
     * @param submission            structured payload from the widget
     * @param legacyTurnstileToken  deprecated top-level token sent by older clients
     */
    public void verify(CaptchaSubmission submission, String legacyTurnstileToken,
                       String remoteIp, boolean required) {
        if (!required) {
            return;
        }
        CaptchaConfig config = settingService.getCaptchaConfig();
        if (config.provider() == CaptchaProvider.NONE) {
            log.warn("Human verification is required but no captcha provider is configured");
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证尚未配置，请联系管理员");
        }

        CaptchaSubmission payload = submission != null
                ? submission
                : CaptchaSubmission.turnstileToken(legacyTurnstileToken);

        switch (config.provider()) {
            case TURNSTILE -> verifyTurnstile(payload, config, remoteIp);
            case GEETEST -> verifyGeeTest(payload, config);
            default -> throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证尚未配置，请联系管理员");
        }
    }

    private void verifyTurnstile(CaptchaSubmission payload, CaptchaConfig config, String remoteIp) {
        if (payload == null || !StringUtils.hasText(payload.token())) {
            throw BusinessException.badRequest("请先完成人机验证");
        }
        turnstileVerifier.verify(payload.token().trim(), config.turnstileSecretKey(), remoteIp);
    }

    private void verifyGeeTest(CaptchaSubmission payload, CaptchaConfig config) {
        if (payload == null
                || !StringUtils.hasText(payload.lotNumber())
                || !StringUtils.hasText(payload.captchaOutput())
                || !StringUtils.hasText(payload.passToken())
                || !StringUtils.hasText(payload.genTime())) {
            throw BusinessException.badRequest("请先完成人机验证");
        }
        geeTestVerifier.verify(payload, config.geetestCaptchaId(), config.geetestCaptchaKey());
    }
}
