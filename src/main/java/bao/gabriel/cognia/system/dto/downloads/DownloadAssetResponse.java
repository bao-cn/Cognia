package bao.gabriel.cognia.system.dto.downloads;

public record DownloadAssetResponse(
        String code,
        String name,
        String platform,
        String arch,
        String packageType,
        String version,
        String downloadUrl,
        String sha256,
        Long fileSize,
        boolean enabled,
        String updatedAt
) {
}
