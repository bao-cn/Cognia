package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WalletAdjustmentRequest(
        @NotNull Long deltaUnits,
        @NotBlank @Size(max = 512) String reason
) {}
