package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.storage.UploadProgressListener;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.storage.AdminStorageSettingResponse;
import bao.gabriel.cognia.system.dto.storage.PresignedUrlResponse;
import bao.gabriel.cognia.system.dto.storage.StorageConnectionTestResponse;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import bao.gabriel.cognia.system.dto.storage.UpdateStorageSettingRequest;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Admin endpoints for the database-backed S3-compatible object storage.
 */
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final ObjectStorageService objectStorageService;
    private final MinioStorageService minioStorageService;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;

    @GetMapping("/settings")
    @PreAuthorize("hasAnyAuthority('storage:read', 'settings:read')")
    public ApiResponse<AdminStorageSettingResponse> getSettings() {
        return ApiResponse.success(objectStorageService.getAdminSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyAuthority('storage:update', 'settings:update')")
    public ApiResponse<AdminStorageSettingResponse> updateSettings(
            @Valid @RequestBody UpdateStorageSettingRequest request) {
        return ApiResponse.success(objectStorageService.updateSettings(request, SecurityUtils.getUserId()));
    }

    @PostMapping("/settings/test")
    @PreAuthorize("hasAnyAuthority('storage:update', 'settings:update')")
    public ApiResponse<StorageConnectionTestResponse> testConnection() {
        return ApiResponse.success(objectStorageService.testConnection());
    }

    /**
     * Applies the configured tmp/ expiration as a real S3 bucket lifecycle
     * rule immediately, instead of waiting for the daily scheduled task.
     */
    @PostMapping("/settings/lifecycle/sync")
    @PreAuthorize("hasAnyAuthority('storage:update', 'settings:update')")
    public ApiResponse<String> syncTempLifecycle() {
        int days = objectStorageService.getAdminSettings().tempRetentionDays();
        return ApiResponse.success(objectStorageService.syncTempLifecycle(days));
    }

    @PostMapping("/objects")
    @PreAuthorize("hasAnyAuthority('storage:update', 'settings:update')")
    public ApiResponse<StoredObjectResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "directory", required = false) String directory) {
        return ApiResponse.success(minioStorageService.upload(file, directory, UploadProgressListener.NOOP));
    }

    /**
     * Authenticated temporary upload for multimodal chat inputs. Objects live
     * under tmp/ and expire through the bucket lifecycle rule.
     */
    @PostMapping("/temp")
    public ApiResponse<StoredObjectResponse> uploadTemp(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(minioStorageService.uploadTemp(file, UploadProgressListener.NOOP));
    }

    /**
     * Stable same-origin avatar URL. Browsers request this without a Bearer
     * token, so the endpoint is public and redirects to a fresh presigned URL.
     */
    @GetMapping("/avatar/{ownerId}")
    public ResponseEntity<Void> avatar(@PathVariable Long ownerId) {
        User user = userMapper.selectById(ownerId);
        String key = user != null && StringUtils.hasText(user.getUsername())
                ? "avatar/" + user.getUsername() + "/avatar.webp"
                : "avatar/" + ownerId + ".webp";
        String url;
        try {
            url = objectStorageService.presignedUrl(key, 3600).url();
        } catch (RuntimeException ex) {
            url = objectStorageService.presignedUrl("avatar/" + ownerId + ".webp", 3600).url();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .build();
    }

    @DeleteMapping("/objects")
    @PreAuthorize("hasAnyAuthority('storage:update', 'settings:update')")
    public ApiResponse<Void> deleteObject(@RequestParam("key") String key) {
        objectStorageService.deleteObject(key);
        return ApiResponse.success();
    }

    @GetMapping("/objects/presigned-url")
    @PreAuthorize("hasAnyAuthority('storage:read', 'settings:read')")
    public ApiResponse<PresignedUrlResponse> presignedUrl(
            @RequestParam("key") String key,
            @RequestParam(value = "expiresInSeconds", required = false) Integer expiresInSeconds) {
        return ApiResponse.success(objectStorageService.presignedUrl(key, expiresInSeconds));
    }

    @GetMapping("/avatar/team/{teamId}")
    public ResponseEntity<Void> teamAvatar(@PathVariable Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        String url = objectStorageService.presignedUrl("avatar/" + teamId + "/avatar.webp", 3600).url();
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(url)).build();
    }
}
