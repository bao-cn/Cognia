package bao.gabriel.cognia.system.dto.channel;

public record ChannelEmbeddingJobResponse(
        Long id,
        Long documentId,
        String step,
        String status,
        Integer attempts,
        Integer maxAttempts,
        String errorMessage,
        String createdAt,
        String finishedAt
) {
}
