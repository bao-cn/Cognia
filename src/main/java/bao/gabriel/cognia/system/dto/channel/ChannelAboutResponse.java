package bao.gabriel.cognia.system.dto.channel;

import java.util.List;

/**
 * Read-only channel facts used by the "About" panel.
 */
public record ChannelAboutResponse(
        String description,
        boolean hasReadme,
        String readmeDocumentId,
        String readmeName,
        boolean archived,
        long subscriberCount,
        long notifyCount,
        boolean embeddingReady,
        long pendingEmbeddingChunks,
        boolean reembedding,
        List<Contributor> contributors,
        List<FileType> fileTypes
) {
    public record Contributor(
            String id,
            String username,
            String nickname,
            String avatar,
            long documentCount
    ) {
    }

    public record FileType(String type, long count, long size) {
    }
}
