package bao.gabriel.cognia.system.service;

public interface MetricEventService {
    void recordQa(Long userId, Long channelId, Long conversationId, Long modelId, String modelName,
                  String status, String errorType, long durationMs, boolean retrievalUsed, boolean retrievalHit,
                  boolean rerankUsed, int citationCount, int llmRounds, boolean refused,
                  int inputTokens, int outputTokens, int embeddingTokens,
                  long retrievalDurationMs, long rerankDurationMs,
                  int promptCacheHitTokens, int promptCacheMissTokens);
}
