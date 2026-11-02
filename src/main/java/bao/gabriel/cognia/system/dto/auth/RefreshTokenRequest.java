package bao.gabriel.cognia.system.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken 不能为空")
        @Size(max = 2048, message = "refreshToken 过长")
        String refreshToken
) {
}
