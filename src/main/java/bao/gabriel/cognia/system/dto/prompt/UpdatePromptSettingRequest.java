package bao.gabriel.cognia.system.dto.prompt;

import jakarta.validation.constraints.Size;

public record UpdatePromptSettingRequest(
        @Size(max = 8000) String systemPrompt,
        @Size(max = 8000) String ragPrompt,
        @Size(max = 8000) String expiredPrompt,
        @Size(max = 8000) String toolPrompt,
        @Size(max = 8000) String compressionPrompt
) {
}
