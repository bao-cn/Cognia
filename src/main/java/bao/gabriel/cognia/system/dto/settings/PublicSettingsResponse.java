package bao.gabriel.cognia.system.dto.settings;

import java.util.List;

/**
 * Public, cache-friendly site configuration. Never contains SMTP credentials.
 */
public record PublicSettingsResponse(
        String siteName,
        String logoUrl,
        String siteDescription,
        List<String> keywords,
        String routeSuffix,
        String defaultLanguage,
        String timezone,
        List<String> allowedUploadTypes,
        boolean registrationEnabled,
        boolean registrationEmailVerification,
        boolean registrationEmailWhitelistEnabled,
        boolean registerEmailCaptchaEnabled,
        boolean loginCaptchaEnabled,
        boolean appleLoginEnabled,
        boolean googleLoginEnabled,
        boolean turnstileConfigured,
        String turnstileSiteKey,
        int teamCreationLimit,
        int teamJoinLimit
) {
}
