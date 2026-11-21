package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.Min;

public record BillingSettingsRequest(
        String defaultPlanId,
        @Min(0) long registrationTokens,
        Integer registrationTokenExpireDays,
        String currency
) {}
