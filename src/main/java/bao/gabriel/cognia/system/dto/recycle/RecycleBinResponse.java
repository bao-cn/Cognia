package bao.gabriel.cognia.system.dto.recycle;

public record RecycleBinResponse(
        Long id,
        Long documentId,
        String ownerType,
        Long ownerId,
        Long channelId,
        String name,
        Long size,
        String contentType,
        Long deletedBy,
        String deleteTime,
        Integer retentionDays,
        String expiresAt
) {
}
