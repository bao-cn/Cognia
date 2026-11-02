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
        boolean thinkingEnabled,
        String reasoningEffort,
        String extraBody,
        Integer dimension,
        String updatedAt
) {
}
