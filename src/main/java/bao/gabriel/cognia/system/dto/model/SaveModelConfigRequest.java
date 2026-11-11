package bao.gabriel.cognia.system.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveModelConfigRequest(
        @Min(1) Long providerId,
        @NotBlank @Size(max = 255) String modelId,
        @Size(max = 32) String modelType,
        @Size(max = 128) String displayName,
        Boolean enabled,
        Boolean visible,
        Boolean supportsTools,
        Boolean supportsText,
        Boolean thinkingEnabled,
        Boolean supportsVision,
        @Size(max = 16) String reasoningEffort,
        @Size(max = 4000) String reasoningLevels,
        @Size(max = 4000) String extraBody,
        @Min(1) @Max(65536) Integer dimension,
        @Min(1000) @Max(2000000) Integer contextWindow,
        @Min(1) @Max(1000000) Integer maxOutputTokens
) {
}
