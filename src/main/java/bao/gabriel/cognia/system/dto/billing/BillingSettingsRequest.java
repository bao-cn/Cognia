package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

public record BillingSettingsRequest(
        Boolean billingEnabled,
        String defaultPlanId,
        @Min(0) long registrationTokens,
        Integer registrationTokenExpireDays,
        String currency,
        @Size(max = 3) String walletCurrency,
        @Min(0) @Max(9) Integer walletFractionalDigits,
        @Size(max = 16) String walletUnitLabel
) {}
