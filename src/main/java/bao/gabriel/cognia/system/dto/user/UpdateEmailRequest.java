package bao.gabriel.cognia.system.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEmailRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128)
        String email,

        @NotBlank(message = "请输入邮箱验证码")
        @Size(min = 6, max = 6, message = "邮箱验证码为 6 位")
        String code,

        @Size(max = 12)
        String twoFactorCode
) {
}
