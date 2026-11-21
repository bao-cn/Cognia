package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SaveTokenPackRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @Min(0) long priceMinor,
        String currency,
        @Min(1) long tokens,
        Integer expireDays,
        Integer sort
) {}
