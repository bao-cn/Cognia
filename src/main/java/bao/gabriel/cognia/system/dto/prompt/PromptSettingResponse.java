package bao.gabriel.cognia.system.dto.prompt;

public record PromptSettingResponse(
        String systemPrompt,
        String ragPrompt,
        String expiredPrompt,
        String toolPrompt,
        String compressionPrompt,
        String updatedAt
) {
}
