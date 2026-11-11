package bao.gabriel.cognia.system.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名或邮箱不能为空")
        @Size(max = 128, message = "用户名或邮箱长度不能超过 128 个字符")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不能超过 64 个字符")
        String password,

        @Size(max = 2048, message = "Turnstile 令牌过长")
        String turnstileToken,

        @Size(max = 12)
        String twoFactorCode,

        Boolean rememberMe
) {
}
