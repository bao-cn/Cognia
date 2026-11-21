package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record TokenGrantRequest(
        @NotBlank String tokenPackId,
        @NotBlank String reason
) {}
