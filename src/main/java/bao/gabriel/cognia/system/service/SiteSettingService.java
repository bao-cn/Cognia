package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.settings.AdminSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.PublicSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.SmtpConfig;
import bao.gabriel.cognia.system.dto.settings.TurnstileConfig;
import bao.gabriel.cognia.system.dto.settings.UpdateSettingsRequest;
import bao.gabriel.cognia.system.entity.SiteSetting;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public interface SiteSettingService {

    SiteSetting getSetting();

    PublicSettingsResponse getPublicSettings();

    AdminSettingsResponse getAdminSettings();

    AdminSettingsResponse updateSettings(UpdateSettingsRequest request, Long operatorId);

    SmtpConfig getSmtpConfig();

    TurnstileConfig getTurnstileConfig();

    boolean isRegistrationEnabled();

    boolean isEmailVerificationRequired();

    boolean isRegisterEmailCaptchaEnabled();

    boolean isLoginCaptchaEnabled();

    boolean isTurnstileConfigured();

    boolean isRegistrationEmailAllowed(String email);

    ZoneId getZoneId();

    String formatDateTime(LocalDateTime value);

    List<String> getAllowedUploadTypes();
}
