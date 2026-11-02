package bao.gabriel.cognia.system.dto.settings;

import java.util.List;

public record MailTemplateResponse(
        String code,
        String name,
        String description,
        String subject,
        String content,
        List<String> placeholders,
        String updatedAt
) {
}
