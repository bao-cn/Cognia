package bao.gabriel.cognia.system.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyCodeRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "\\d{6}", message = "请输入 6 位数字验证码")
        String code
) {
}
