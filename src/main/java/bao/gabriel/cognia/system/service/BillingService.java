package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.billing.*;
import bao.gabriel.cognia.system.entity.*;

import java.util.List;
import java.util.Map;

public interface BillingService {
    BillingSetting getSettings();
    BillingSetting updateSettings(BillingSettingsRequest request);

    PageResult<BillingPlan> pagePlans(long page, long size);
    BillingPlan getPlan(Long id);
    BillingPlan createPlan(SavePlanRequest request);
    BillingPlan updatePlan(Long id, SavePlanRequest request);
    void replacePlanModels(Long id, List<String> modelIds);
    void publishPlan(Long id);
    void disablePlan(Long id);

    PageResult<TokenPack> pageTokenPacks(long page, long size);
    TokenPack getTokenPack(Long id);
    TokenPack createTokenPack(SaveTokenPackRequest request);
    TokenPack updateTokenPack(Long id, SaveTokenPackRequest request);
    void disableTokenPack(Long id);

    Map<String, Object> catalog();
    Map<String, Object> me(Long userId);
    PageResult<TokenLedger> ledger(Long userId, long page, long size);
    PageResult<TokenUsage> usage(Long userId, long page, long size, Long modelId, String status, String from, String to);

    BillingOrder createOrder(Long userId, String idempotencyKey, CreateOrderRequest request);
    BillingOrder getOrder(Long userId, String orderNo);
    void cancelOrder(Long userId, String orderNo);
    PageResult<BillingOrder> pageOrders(long page, long size, String status, Long userId);

    Map<String, Object> usageSummary(String from, String to);
    PageResult<Map<String, Object>> usageByModel(long page, long size, String from, String to);
    List<Map<String, Object>> usageTrend(String from, String to, String interval);

    Map<String, Object> grantTokens(Long userId, String idempotencyKey, TokenGrantRequest request);
    Map<String, Object> adjustTokens(Long userId, Map<String, Object> request);
    Map<String, Object> adminUserBilling(Long userId, String from, String to);

    void initializeNewUser(Long userId);
    void reserveChatTokens(Long userId, String requestId, long estimatedTokens);
    void settleChatTokens(Long userId, String requestId, Long modelId, Long providerId,
                          String modelName, long inputTokens, long outputTokens, long durationMs,
                          String status, Long conversationId);
    void releaseChatReservation(Long userId, String requestId);
    boolean processPaymentWebhook(String provider, String eventId, String transactionId,
                                  String orderNo, long amountMinor, String currency,
                                  String payloadHash, String rawPayload);
}
