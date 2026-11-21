package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CreateRedeemCodeBatchRequest(
        @NotBlank String benefitType,
        String productId,
        @Min(1) @Max(1000) int quantity,
        @Min(1) Long amountUnits,
        LocalDateTime validFrom,
        LocalDateTime expireTime
) {}
