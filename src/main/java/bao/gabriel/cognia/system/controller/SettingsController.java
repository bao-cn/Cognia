package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.settings.AdminSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.PublicSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.SmtpTestRequest;
import bao.gabriel.cognia.system.dto.settings.UpdateSettingsRequest;
import bao.gabriel.cognia.system.service.EmailService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SiteSettingService settingService;
    private final EmailService emailService;

    /**
     * Public branding/auth switches consumed by the SPA on boot. No secrets.
     */
    @GetMapping("/public")
    public ApiResponse<PublicSettingsResponse> publicSettings() {
        return ApiResponse.success(settingService.getPublicSettings());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('settings:read')")
    public ApiResponse<AdminSettingsResponse> settings() {
        return ApiResponse.success(settingService.getAdminSettings());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<AdminSettingsResponse> update(@Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.success(settingService.updateSettings(request, SecurityUtils.getUserId()));
    }

    @PostMapping("/smtp/test")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<Void> testSmtp(@Valid @RequestBody SmtpTestRequest request) {
        emailService.sendTestEmail(request.recipient());
        return ApiResponse.success();
    }
}
