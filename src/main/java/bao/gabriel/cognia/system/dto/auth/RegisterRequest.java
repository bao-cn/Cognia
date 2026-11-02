package bao.gabriel.cognia.system.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "用户名需以字母开头，只能包含字母、数字、下划线，长度 3-32")
        String username,

        @Size(max = 32, message = "昵称长度不能超过 32 个字符")
        String nickname,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        String email,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$",
                message = "密码长度需为 8-32 位，且同时包含字母和数字")
        String password,

        @Pattern(regexp = "^$|^\\d{6}$", message = "验证码为 6 位数字")
        String code
) {
}
