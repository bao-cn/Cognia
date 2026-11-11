package bao.gabriel.cognia.rag.dto;

/**
 * One numbered source shown below a RAG answer. The number matches the [n]
 * marker the model is instructed to emit in the answer text.
 */
public record CitationSource(
        int index,
        Long documentId,
        Long channelId,
        String channelName,
        String channelSlug,
        String ownerType,
        Long ownerId,
        String fileName,
        String title,
        String documentType,
        Integer pageNumber,
        Integer chunkIndex,
        String snippet,
        Double score,
        String url,
        String kind
) {
    public CitationSource(int index, Long documentId, Long channelId, String channelName, String channelSlug,
                          String ownerType, Long ownerId, String fileName, String title, String documentType,
                          Integer pageNumber, Integer chunkIndex, String snippet, Double score) {
        this(index, documentId, channelId, channelName, channelSlug, ownerType, ownerId, fileName, title,
                documentType, pageNumber, chunkIndex, snippet, score, null, "DOCUMENT");
    }
}
