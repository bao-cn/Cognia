package bao.gabriel.cognia.rag.dto;

import java.util.List;

/** Retrieval result plus truthful rerank execution metadata. */
public record RetrievalOutcome(
        List<RetrievedChunk> chunks,
        boolean rerankUsed,
        long rerankDurationMs,
        String rerankError
) {}
