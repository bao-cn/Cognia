package bao.gabriel.cognia.system.dto.channel;

public record DocumentChunkResponse(
        Long id,
        String chunkId,
        Integer chunkIndex,
        String text,
        Integer pageNumber,
        String title,
        String embeddingModel,
        String embeddingStatus
) {
}
