package bao.gabriel.cognia.system.dto.settings;

import java.util.List;

/**
 * Administrative view of the settings. The SMTP password is never returned;
 * clients only learn whether one is configured.
 */
public record AdminSettingsResponse(
        String logoUrl,
        String siteName,
        String siteDescription,
        List<String> keywords,
        String routeSuffix,
        String defaultLanguage,
        String timezone,
        List<String> allowedUploadTypes,
        boolean registrationEnabled,
        boolean registrationEmailVerification,
        boolean registerEmailCaptchaEnabled,
        boolean loginCaptchaEnabled,
        boolean registrationEmailWhitelistEnabled,
        List<String> registrationEmailWhitelist,
        boolean appleLoginEnabled,
        boolean googleLoginEnabled,
        String turnstileSiteKey,
        boolean turnstileSecretKeyConfigured,
        boolean smtpEnabled,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        boolean smtpPasswordConfigured,
        String smtpFrom,
        boolean smtpSsl,
        boolean smtpStarttls,
        boolean smtpAuth,
        int teamCreationLimit,
        int teamJoinLimit,
        int maxFolderDepth,
        int recycleRetentionDays,
        String updatedAt
) {
}
