package bao.gabriel.cognia.system.dto.storage;

/**
 * Administrative view of the object storage configuration. The secret access
 * key is never returned; clients only learn whether one is configured.
 */
public record AdminStorageSettingResponse(
        boolean enabled,
        boolean configured,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        boolean secretKeyConfigured,
        boolean pathStyleAccess,
        String publicBaseUrl,
        String objectPrefix,
        int maxFileSizeMb,
        int presignedUrlExpirySeconds,
        int tempRetentionDays,
        String updatedAt
) {
}
