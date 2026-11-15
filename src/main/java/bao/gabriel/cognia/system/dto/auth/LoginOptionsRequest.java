package bao.gabriel.cognia.system.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginOptionsRequest(@NotBlank(message = "请输入用户名或邮箱") String account) {
}
