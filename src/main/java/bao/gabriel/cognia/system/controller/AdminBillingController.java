package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.billing.*;
import bao.gabriel.cognia.system.entity.*;
import bao.gabriel.cognia.system.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
public class AdminBillingController {
    private final BillingService billingService;

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('billing:read')")
    public ApiResponse<BillingSetting> settings() { return ApiResponse.success(billingService.getSettings()); }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<BillingSetting> updateSettings(@Valid @RequestBody BillingSettingsRequest request) {
        return ApiResponse.success(billingService.updateSettings(request));
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('billing:read')")
    public ApiResponse<PageResult<BillingPlan>> plans(@RequestParam(defaultValue="1") long page,
                                                       @RequestParam(defaultValue="20") long size) {
        return ApiResponse.success(billingService.pagePlans(page, size));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<BillingPlan> createPlan(@Valid @RequestBody SavePlanRequest request) {
        return ApiResponse.success(billingService.createPlan(request));
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('billing:read')")
    public ApiResponse<BillingPlan> plan(@PathVariable Long id) { return ApiResponse.success(billingService.getPlan(id)); }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<BillingPlan> updatePlan(@PathVariable Long id, @Valid @RequestBody SavePlanRequest request) {
        return ApiResponse.success(billingService.updatePlan(id, request));
    }

    @PutMapping("/plans/{id}/models")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<Void> replaceModels(@PathVariable Long id, @RequestBody ReplacePlanModelsRequest request) {
        billingService.replacePlanModels(id, request.modelIds());
        return ApiResponse.success();
    }

    @PostMapping("/plans/{id}/publish")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<Void> publish(@PathVariable Long id) { billingService.publishPlan(id); return ApiResponse.success(); }

    @PostMapping("/plans/{id}/disable")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<Void> disable(@PathVariable Long id) { billingService.disablePlan(id); return ApiResponse.success(); }

    @GetMapping("/token-packs")
    @PreAuthorize("hasAuthority('billing:read')")
    public ApiResponse<PageResult<TokenPack>> packs(@RequestParam(defaultValue="1") long page,
                                                     @RequestParam(defaultValue="20") long size) {
        return ApiResponse.success(billingService.pageTokenPacks(page, size));
    }

    @PostMapping("/token-packs")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<TokenPack> createPack(@Valid @RequestBody SaveTokenPackRequest request) {
        return ApiResponse.success(billingService.createTokenPack(request));
    }

    @GetMapping("/token-packs/{id}")
    @PreAuthorize("hasAuthority('billing:read')")
    public ApiResponse<TokenPack> pack(@PathVariable Long id) { return ApiResponse.success(billingService.getTokenPack(id)); }

    @PutMapping("/token-packs/{id}")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<TokenPack> updatePack(@PathVariable Long id, @Valid @RequestBody SaveTokenPackRequest request) {
        return ApiResponse.success(billingService.updateTokenPack(id, request));
    }

    @PostMapping("/token-packs/{id}/disable")
    @PreAuthorize("hasAuthority('billing:update')")
    public ApiResponse<Void> disablePack(@PathVariable Long id) { billingService.disableTokenPack(id); return ApiResponse.success(); }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('billing:order-read')")
    public ApiResponse<PageResult<BillingOrder>> orders(@RequestParam(defaultValue="1") long page,
                                                         @RequestParam(defaultValue="20") long size,
                                                         @RequestParam(required=false) String status,
                                                         @RequestParam(required=false) Long userId) {
        return ApiResponse.success(billingService.pageOrders(page, size, status, userId));
    }

    @GetMapping("/usage/summary")
    @PreAuthorize("hasAuthority('billing:usage-read')")
    public ApiResponse<Map<String, Object>> usageSummary(@RequestParam(required=false) String from,
                                                         @RequestParam(required=false) String to) {
        return ApiResponse.success(billingService.usageSummary(from, to));
    }

    @GetMapping("/usage/by-model")
    @PreAuthorize("hasAuthority('billing:usage-read')")
    public ApiResponse<PageResult<Map<String, Object>>> usageByModel(@RequestParam(defaultValue="1") long page,
                                                                      @RequestParam(defaultValue="20") long size,
                                                                      @RequestParam(required=false) String from,
                                                                      @RequestParam(required=false) String to) {
        return ApiResponse.success(billingService.usageByModel(page, size, from, to));
    }

    @GetMapping("/usage/trend")
    @PreAuthorize("hasAuthority('billing:usage-read')")
    public ApiResponse<List<Map<String, Object>>> usageTrend(@RequestParam(required=false) String from,
                                                              @RequestParam(required=false) String to,
                                                              @RequestParam(defaultValue="DAY") String interval) {
        return ApiResponse.success(billingService.usageTrend(from, to, interval));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('billing:usage-read')")
    public ApiResponse<Map<String, Object>> userBilling(@PathVariable Long userId,
                                                         @RequestParam(required=false) String from,
                                                         @RequestParam(required=false) String to) {
        return ApiResponse.success(billingService.adminUserBilling(userId, from, to));
    }

    @GetMapping("/users/{userId}/usage")
    @PreAuthorize("hasAuthority('billing:usage-read')")
    public ApiResponse<PageResult<TokenUsage>> userUsage(@PathVariable Long userId,
                                                          @RequestParam(defaultValue="1") long page,
                                                          @RequestParam(defaultValue="20") long size,
                                                          @RequestParam(required=false) Long modelId,
                                                          @RequestParam(required=false) String status,
                                                          @RequestParam(required=false) String from,
                                                          @RequestParam(required=false) String to) {
        return ApiResponse.success(billingService.usage(userId, page, size, modelId, status, from, to));
    }

    @GetMapping("/users/{userId}/ledger")
    @PreAuthorize("hasAuthority('billing:usage-read')")
    public ApiResponse<PageResult<TokenLedger>> userLedger(@PathVariable Long userId,
                                                            @RequestParam(defaultValue="1") long page,
                                                            @RequestParam(defaultValue="20") long size) {
        return ApiResponse.success(billingService.ledger(userId, page, size));
    }

    @PostMapping("/users/{userId}/token-grants")
    @PreAuthorize("hasAuthority('billing:grant')")
    public ApiResponse<Map<String, Object>> grant(@PathVariable Long userId,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody TokenGrantRequest request) {
        return ApiResponse.success(billingService.grantTokens(userId, idempotencyKey, request));
    }

    @PostMapping("/users/{userId}/adjustments")
    @PreAuthorize("hasAuthority('billing:adjust')")
    public ApiResponse<Map<String, Object>> adjust(@PathVariable Long userId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(billingService.adjustTokens(userId, request));
    }
}
