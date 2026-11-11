package bao.gabriel.cognia.system.dto.user;

import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
        @Size(max = 128) String password,
        @Size(max = 12) String twoFactorCode
) {
}
