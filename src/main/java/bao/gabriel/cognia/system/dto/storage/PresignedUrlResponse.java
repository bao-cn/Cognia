package bao.gabriel.cognia.system.dto.storage;

public record PresignedUrlResponse(
        String key,
        String url,
        int expiresInSeconds,
        String expiresAt
) {
}
