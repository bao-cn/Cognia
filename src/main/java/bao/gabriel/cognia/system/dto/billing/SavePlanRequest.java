package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SavePlanRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @Min(0) long priceMinor,
        String currency,
        @Min(1) int durationDays,
        @Min(0) long quotaTokens,
        @Min(1) int quotaCycleDays,
        List<String> modelIds,
        Integer sort
) {}
