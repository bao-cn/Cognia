package bao.gabriel.cognia.system.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateRagSettingRequest(
        Long chatModelId,
        Long embeddingModelId,
        Boolean rerankEnabled,
        Long rerankModelId,
        @Min(100) @Max(8000) Integer chunkSize,
        @Min(0) @Max(2000) Integer chunkOverlap,
        @Min(1) @Max(100) Integer retrievalTopK,
        @Min(1) @Max(50) Integer retrievalTopN,
        @Size(max = 512) String webSearchEndpoint,
        @Size(max = 2048) String webSearchApiKey,
        Boolean clearWebSearchApiKey
) {
}
