package bao.gabriel.cognia.system.dto.channel;

public record ChannelDocumentResponse(
        String id,
        String name,
        String folderId,
        long size,
        String contentType,
        String status,
        String errorMessage,
        int retryCount,
        String title,
        String documentType,
        String expirationTime,
        String createTime,
        String processedAt
) {
}
