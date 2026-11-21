package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record RedeemCodeRequest(@NotBlank String code) {}
