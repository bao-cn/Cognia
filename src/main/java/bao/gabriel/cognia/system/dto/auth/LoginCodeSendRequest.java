package bao.gabriel.cognia.system.dto.auth;

import bao.gabriel.cognia.security.CaptchaSubmission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginCodeSendRequest(
        @NotBlank(message = "请输入用户名或邮箱")
        @Size(max = 128, message = "账号长度不能超过 128 个字符")
        String account,

        @Valid
        CaptchaSubmission captcha,

        @Size(max = 2048, message = "Turnstile 令牌过长")
        String turnstileToken
) {
}
