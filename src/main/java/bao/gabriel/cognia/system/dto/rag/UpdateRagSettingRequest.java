package bao.gabriel.cognia.system.dto.rag;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateRagSettingRequest(
        Long chatModelId,
        Long embeddingModelId,
        Boolean rerankEnabled,
        Long rerankModelId,
        @Min(100) @Max(8000) Integer chunkSize,
        @Min(0) @Max(2000) Integer chunkOverlap,
        @Min(1) @Max(100) Integer retrievalTopK,
        @Min(1) @Max(50) Integer retrievalTopN,
        Boolean webSearchEnabled,
        Boolean allowUserWebSearch,
        @Min(1) @Max(100) Integer contextCompressionThreshold,
        Boolean semanticCacheEnabled,
        Boolean retrievalCacheEnabled,
        @DecimalMin("0.5") @DecimalMax("1.0") Double semanticCacheThreshold,
        @Min(1) @Max(10080) Integer semanticCacheTtlMinutes,
        @Min(1) @Max(100000) Integer semanticCacheMaxEntries,
        @Min(1) @Max(10080) Integer retrievalCacheTtlMinutes,
        @Min(1) @Max(100000) Integer retrievalCacheMaxEntries,
        Boolean titleGenerationEnabled
) {
}
