package bao.gabriel.cognia.system.dto.storage;

/**
 * Metadata returned after an object has been stored. When no public base URL
 * is configured, {@code url} is a temporary presigned URL.
 */
public record StoredObjectResponse(
        String key,
        String url,
        long size,
        String contentType,
        String uploadedAt
) {
}
