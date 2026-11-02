package bao.gabriel.cognia.system.dto.model;

public record PublicModelResponse(
        Long id,
        Long providerId,
        String providerName,
        String modelId,
        String displayName,
        String modelType,
        boolean supportsTools,
        boolean thinkingEnabled
) {
}
