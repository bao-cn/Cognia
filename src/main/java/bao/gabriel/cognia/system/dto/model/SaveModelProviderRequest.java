package bao.gabriel.cognia.system.dto.model;

import jakarta.validation.constraints.Size;

public record SaveModelProviderRequest(
        @Size(max = 128) String name,
        @Size(max = 32) String vendor,
        @Size(max = 512) String baseUrl,
        @Size(max = 2048) String apiKey,
        Boolean clearApiKey,
        Boolean enabled,
        /** OPENAI_CHAT | OPENAI_RESPONSE | ANTHROPIC_MESSAGES */
        @Size(max = 32) String protocol
) {
}
