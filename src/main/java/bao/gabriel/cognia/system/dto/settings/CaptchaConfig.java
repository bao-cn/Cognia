package bao.gabriel.cognia.system.dto.settings;

/**
 * Effective human-verification capability. {@code provider} is {@link
 * CaptchaProvider#NONE} when the selected provider is disabled or not fully
 * configured, so callers can treat any other value as "ready to verify".
 * The secret values are decrypted by {@code SiteSettingService}.
 */
public record CaptchaConfig(
        CaptchaProvider provider,
        String turnstileSiteKey,
        String turnstileSecretKey,
        String geetestCaptchaId,
        String geetestCaptchaKey
) {
}
