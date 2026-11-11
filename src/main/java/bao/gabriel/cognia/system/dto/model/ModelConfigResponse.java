package bao.gabriel.cognia.system.dto.model;

public record ModelConfigResponse(
        Long id,
        Long providerId,
        String providerName,
        String modelId,
        String displayName,
        String modelType,
        boolean enabled,
        boolean visible,
        boolean supportsTools,
        boolean supportsText,
        boolean thinkingEnabled,
        boolean supportsVision,
        String reasoningEffort,
        String reasoningLevels,
        String extraBody,
        Integer dimension,
        Integer contextWindow,
        Integer maxOutputTokens,
        String updatedAt
) {
}
