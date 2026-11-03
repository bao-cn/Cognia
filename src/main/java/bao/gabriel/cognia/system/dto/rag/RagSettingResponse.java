package bao.gabriel.cognia.system.dto.rag;

public record RagSettingResponse(
        Long chatModelId,
        Long embeddingModelId,
        boolean rerankEnabled,
        Long rerankModelId,
        int chunkSize,
        int chunkOverlap,
        int retrievalTopK,
        int retrievalTopN,
        String webSearchEndpoint,
        boolean webSearchApiKeyConfigured,
        String updatedAt,
        boolean webSearchEnabled,
        boolean allowUserWebSearch,
        int contextCompressionThreshold,
        String compressionPrompt,
        boolean semanticCacheEnabled,
        boolean retrievalCacheEnabled,
        double semanticCacheThreshold,
        int semanticCacheTtlMinutes,
        int semanticCacheMaxEntries,
        int retrievalCacheTtlMinutes,
        int retrievalCacheMaxEntries,
        boolean titleGenerationEnabled
) {
}
