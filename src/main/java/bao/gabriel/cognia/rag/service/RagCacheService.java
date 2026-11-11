package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.system.entity.RagSetting;

import java.util.List;

/**
 * Two-layer cache for the RAG chat pipeline.
 *
 * <p>The semantic layer maps a query embedding to a previously generated
 * answer, scoped by viewer and channel selection. The retrieval layer maps a
 * normalized query hash to the chunks returned by the vector search. Writes are
 * asynchronous and best-effort: a cache failure never fails a chat request.</p>
 */
public interface RagCacheService {

    record SemanticHit(String answer, String contextJson, double similarity, String cacheKey, long entryHits) {
    }

    record RetrievalHit(List<RetrievedChunk> chunks, String cacheKey, long entryHits) {
    }

    record Stats(
            long semanticEntries,
            long retrievalEntries,
            long semanticHits,
            long retrievalHits,
            long hits,
            long misses,
            long requests,
            double hitRate
    ) {
    }

    /**
     * SHA-256 of viewer id plus the sorted channel ids; keeps caches isolated
     * per user and channel selection.
     */
    String scopeHash(Long viewerId, List<Long> channelIds);

    /**
     * Stable, non-reversible cache key for a query in a scope (used in events
     * and as the stampede lock key).
     */
    String cacheKey(String scopeHash, String query);

    SemanticHit findSemantic(String scopeHash, String query, float[] queryEmbedding, RagSetting setting);

    RetrievalHit findRetrieval(String scopeHash, String query, RagSetting setting);

    void storeSemanticAsync(String scopeHash, String query, String answer, String contextJson,
                            float[] embedding, RagSetting setting);

    void storeRetrievalAsync(String scopeHash, String query, List<RetrievedChunk> chunks, RagSetting setting);

    void recordRequest();

    void recordHit(String hitType);

    void recordMiss();

    long invalidateDocument(Long documentId);

    long clearSemantic();

    long clearRetrieval();

    Stats stats();
}
