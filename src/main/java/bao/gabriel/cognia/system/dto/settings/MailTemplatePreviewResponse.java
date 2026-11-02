package bao.gabriel.cognia.system.dto.settings;

public record MailTemplatePreviewResponse(
        String subject,
        String html,
        String text
) {
}
