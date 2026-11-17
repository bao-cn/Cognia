package bao.gabriel.cognia.system.dto.channel;

public record ChannelProcessingRecord(
        Long documentId,
        String name,
        String documentType,
        Long size,
        String uploadedAt,
        String status,
        String stage,
        long chunkTotal,
        long chunkDone,
        int attempts,
        String errorMessage,
        String updatedAt,
        String batchId,
        boolean reembedding
) {
}
