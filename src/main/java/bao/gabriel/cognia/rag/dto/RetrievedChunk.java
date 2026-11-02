package bao.gabriel.cognia.rag.dto;

import java.time.LocalDateTime;

public record RetrievedChunk(
        Long id,
        Long documentId,
        Long channelId,
        String channelName,
        String channelSlug,
        String ownerType,
        Long ownerId,
        boolean channelPublic,
        String fileName,
        String chunkText,
        Integer chunkIndex,
        Integer pageNumber,
        String title,
        String author,
        String documentType,
        String s3Path,
        String embeddingModel,
        LocalDateTime expirationTime,
        boolean expired,
        double score
) {
}
