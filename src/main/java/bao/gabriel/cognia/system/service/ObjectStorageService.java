package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.storage.AdminStorageSettingResponse;
import bao.gabriel.cognia.system.dto.storage.PresignedUrlResponse;
import bao.gabriel.cognia.system.dto.storage.StorageConnectionTestResponse;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import bao.gabriel.cognia.system.dto.storage.UpdateStorageSettingRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3-compatible object storage whose connection parameters live in the
 * database and are maintained from the admin console.
 */
public interface ObjectStorageService {

    AdminStorageSettingResponse getAdminSettings();

    AdminStorageSettingResponse updateSettings(UpdateStorageSettingRequest request, Long operatorId);

    StorageConnectionTestResponse testConnection();

    boolean isEnabled();

    boolean isConfigured();

    StoredObjectResponse upload(MultipartFile file, String directory);

    /**
     * Deletes every object under a prefix (used by periodic cleanup tasks).
     * Returns the number of deleted keys.
     */
    int deletePrefix(String prefix);

    /**
     * Aborts multipart uploads under a prefix that started before the cutoff.
     */
    int abortIncompleteMultipartUploads(String prefix, int olderThanHours, int limit);

    /**
     * Installs/updates the S3 lifecycle rule that expires tmp/ objects.
     */
    String syncTempLifecycle(int retentionDays);

    void deleteObject(String key);

    PresignedUrlResponse presignedUrl(String key, Integer expiresInSeconds);
}
