package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.billing.CreateOrderRequest;
import bao.gabriel.cognia.system.entity.BillingOrder;
import bao.gabriel.cognia.system.entity.TokenLedger;
import bao.gabriel.cognia.system.entity.TokenUsage;
import bao.gabriel.cognia.system.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService billingService;

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> catalog() { return ApiResponse.success(billingService.catalog()); }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> me() { return ApiResponse.success(billingService.me(SecurityUtils.getUserId())); }

    @GetMapping("/me/ledger")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<TokenLedger>> ledger(@RequestParam(defaultValue="1") long page,
                                                        @RequestParam(defaultValue="20") long size) {
        return ApiResponse.success(billingService.ledger(SecurityUtils.getUserId(), page, size));
    }

    @GetMapping("/me/usage")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<TokenUsage>> usage(@RequestParam(defaultValue="1") long page,
                                                      @RequestParam(defaultValue="20") long size,
                                                      @RequestParam(required=false) Long modelId,
                                                      @RequestParam(required=false) String status,
                                                      @RequestParam(required=false) String from,
                                                      @RequestParam(required=false) String to) {
        return ApiResponse.success(billingService.usage(SecurityUtils.getUserId(), page, size, modelId, status, from, to));
    }

    @PostMapping("/orders")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BillingOrder> createOrder(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(billingService.createOrder(SecurityUtils.getUserId(), idempotencyKey, request));
    }

    @GetMapping("/orders/{orderNo}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<BillingOrder> order(@PathVariable String orderNo) {
        return ApiResponse.success(billingService.getOrder(SecurityUtils.getUserId(), orderNo));
    }

    @PostMapping("/orders/{orderNo}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> cancel(@PathVariable String orderNo) {
        billingService.cancelOrder(SecurityUtils.getUserId(), orderNo);
        return ApiResponse.success();
    }
}
