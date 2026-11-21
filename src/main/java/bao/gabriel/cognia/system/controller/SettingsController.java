package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.settings.AdminSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.PublicSettingsResponse;
import bao.gabriel.cognia.system.dto.settings.SmtpTestRequest;
import bao.gabriel.cognia.system.dto.settings.UpdateSettingsRequest;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.storage.UploadProgressListener;
import bao.gabriel.cognia.system.entity.SiteSetting;
import bao.gabriel.cognia.system.service.EmailService;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SiteSettingService settingService;
    private final EmailService emailService;
    private final MinioStorageService minioStorageService;

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

    @PostMapping("/logo")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<Map<String, String>> uploadLogo(@org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择 Logo 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!java.util.Set.of("png", "jpg", "jpeg", "webp", "gif").contains(ext)) {
            throw BusinessException.badRequest("Logo 仅支持 PNG / JPG / WebP / GIF");
        }
        String key = "branding/site-logo." + ("jpg".equals(ext) ? "jpeg" : ext);
        minioStorageService.uploadAssetAtKey(file, key, UploadProgressListener.NOOP);
        String url = "/api/v1/settings/logo?v=" + System.currentTimeMillis();
        settingService.updateBrandLogo(url, key, SecurityUtils.getUserId());
        return ApiResponse.success(Map.of("url", url));
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        SiteSetting setting = settingService.getSetting();
        String key = setting.getLogoObjectKey();
        if (key == null || key.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = minioStorageService.readObject(key);
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase();
        MediaType type = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(bytes);
    }

    @PostMapping("/smtp/test")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<Void> testSmtp(@Valid @RequestBody SmtpTestRequest request) {
        emailService.sendTestEmail(request.recipient());
        return ApiResponse.success();
    }
}
