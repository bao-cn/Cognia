package bao.gabriel.cognia.system.dto.legal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitConsentRequest(
        @NotNull(message = "隐私政策版本不能为空")
        @Min(value = 1, message = "隐私政策版本不合法")
        Integer privacyVersion,

        @NotNull(message = "用户协议版本不能为空")
        @Min(value = 1, message = "用户协议版本不合法")
        Integer termsVersion
) {
}
