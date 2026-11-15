package bao.gabriel.cognia.system.dto.recycle;

public record AdminRecycleBinResponse(
        Long id,
        Long documentId,
        String ownerType,
        Long ownerId,
        String ownerName,
        Long channelId,
        String channelName,
        String channelSlug,
        String name,
        Long size,
        String contentType,
        Long deletedBy,
        String deletedByName,
        String deleteTime,
        Integer retentionDays,
        String expiresAt
) {
}
