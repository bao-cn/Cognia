package bao.gabriel.cognia.system.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
        @NotBlank String productType,
        @NotBlank String productId,
        @NotBlank String paymentProvider,
        String returnUrl
) {}
