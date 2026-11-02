package bao.gabriel.cognia.system.dto.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SmtpTestRequest(
        @NotBlank(message = "收件人邮箱不能为空")
        @Email(message = "收件人邮箱格式不正确")
        String recipient
) {
}
