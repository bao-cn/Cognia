package bao.gabriel.cognia.system.dto.auth;

import bao.gabriel.cognia.security.CaptchaSubmission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginTwoFactorRequest(
        @NotBlank(message = "请输入用户名或邮箱")
        String account,
        @NotBlank(message = "请输入两步验证码")
        @Pattern(regexp = "\\d{6}", message = "请输入 6 位数字验证码")
        String code,
        Boolean rememberMe,
        @Valid
        CaptchaSubmission captcha,
        String turnstileToken
) {
}
