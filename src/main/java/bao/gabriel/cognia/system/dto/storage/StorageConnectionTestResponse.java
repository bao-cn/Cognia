package bao.gabriel.cognia.system.dto.storage;

/**
 * Result of a full bucket probe: access, write, read and cleanup.
 */
public record StorageConnectionTestResponse(
        boolean success,
        boolean bucketExists,
        boolean canWrite,
        boolean canRead,
        boolean canDelete,
        long latencyMs,
        String endpoint,
        String region,
        String bucket,
        String message,
        String testedAt
) {
}
