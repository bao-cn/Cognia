package bao.gabriel.cognia.system.dto.twofactor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorCodeRequest(
        @NotBlank(message = "请输入两步验证码")
        @Size(max = 12)
        String code
) {
}
