package bao.gabriel.cognia.system.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginEmailCodeRequest(
        @NotBlank(message = "请输入用户名或邮箱")
        @Size(max = 128, message = "账号长度不能超过 128 个字符")
        String account,

        @NotBlank(message = "请输入邮箱验证码")
        @Pattern(regexp = "\\d{6}", message = "请输入 6 位数字验证码")
        String code,

        Boolean rememberMe,

        @Size(max = 2048, message = "Turnstile 令牌过长")
        String turnstileToken
) {
}
