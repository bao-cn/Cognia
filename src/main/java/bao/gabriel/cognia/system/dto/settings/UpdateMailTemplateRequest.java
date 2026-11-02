package bao.gabriel.cognia.system.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMailTemplateRequest(
        @NotBlank(message = "邮件主题不能为空")
        @Size(max = 255, message = "邮件主题长度不能超过 255 个字符")
        String subject,

        @NotBlank(message = "邮件内容不能为空")
        @Size(max = 20000, message = "邮件内容过长")
        String content
) {
}
