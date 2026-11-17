package bao.gabriel.cognia.system.dto.channel;

import java.util.List;

public record ChannelEmbeddingResponse(
        boolean embeddingReady,
        long pendingChunks,
        boolean reembedding,
        int totalDocuments,
        int completedDocuments,
        long totalChunks,
        long embeddedChunks,
        List<ChannelProcessingRecord> records,
        List<ChannelEmbeddingJobResponse> jobs
) {
}
