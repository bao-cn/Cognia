package bao.gabriel.cognia.system.dto.oauth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OAuthRegistrationRequest(
        @NotBlank(message = "注册请求无效") String registrationToken,
        @NotBlank(message = "请选择唯一标识")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
                message = "唯一标识需以字母开头，只能包含字母、数字、下划线，长度 3-32")
        String username
) {
}
