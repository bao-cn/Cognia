package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.entity.*;
import bao.gabriel.cognia.system.mapper.*;
import bao.gabriel.cognia.system.dto.billing.*;
import bao.gabriel.cognia.system.service.BillingService;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.security.SecurityUtils;
import com.alibaba.fastjson2.JSON;
import org.springframework.scheduling.annotation.Scheduled;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillingSettingMapper settingMapper;
    private final BillingPlanMapper planMapper;
    private final BillingPlanModelMapper planModelMapper;
    private final TokenPackMapper packMapper;
    private final BillingOrderMapper orderMapper;
    private final TokenLotMapper lotMapper;
    private final TokenLedgerMapper ledgerMapper;
    private final UserSubscriptionMapper subscriptionMapper;
    private final TokenUsageMapper usageMapper;
    private final AdminTokenGrantMapper grantMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final TokenReservationMapper reservationMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentWebhookEventMapper webhookEventMapper;
    private final BillingNotificationOutboxMapper outboxMapper;
    private final ModelBillingRateMapper billingRateMapper;
    private final NotificationService notificationService;

    @Override
    public BillingSetting getSettings() {
        BillingSetting setting = settingMapper.selectById(1L);
        if (setting == null) {
            setting = new BillingSetting();
            setting.setId(1L);
            setting.setRegistrationTokens(0L);
            setting.setCurrency("CNY");
            settingMapper.insert(setting);
        }
        return setting;
    }

    @Override
    @Transactional
    public BillingSetting updateSettings(BillingSettingsRequest request) {
        BillingSetting setting = getSettings();
        if (request.defaultPlanId() != null && !request.defaultPlanId().isBlank()) {
            Long planId = Long.valueOf(request.defaultPlanId());
            BillingPlan plan = planMapper.selectById(planId);
            if (plan == null) throw BusinessException.notFound("默认方案不存在");
            setting.setDefaultPlanId(planId);
        }
        setting.setRegistrationTokens(request.registrationTokens());
        setting.setRegistrationTokenExpireDays(request.registrationTokenExpireDays());
        setting.setCurrency(request.currency() == null || request.currency().isBlank() ? "CNY" : request.currency());
        setting.setUpdateTime(LocalDateTime.now());
        settingMapper.updateById(setting);
        return setting;
    }

    @Override
    public PageResult<BillingPlan> pagePlans(long page, long size) {
        Page<BillingPlan> result = planMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BillingPlan>().orderByAsc(BillingPlan::getSort).orderByDesc(BillingPlan::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public BillingPlan getPlan(Long id) {
        BillingPlan plan = planMapper.selectById(id);
        if (plan == null) throw BusinessException.notFound("订阅方案不存在");
        return plan;
    }

    @Override
    @Transactional
    public BillingPlan createPlan(SavePlanRequest request) {
        BillingPlan plan = new BillingPlan();
        applyPlan(plan, request);
        plan.setStatus("DISABLED");
        plan.setPlanType("PAID");
        plan.setVersion(1);
        plan.setIsDefault(false);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.insert(plan);
        replacePlanModels(plan.getId(), request.modelIds());
        return plan;
    }

    @Override
    @Transactional
    public BillingPlan updatePlan(Long id, SavePlanRequest request) {
        BillingPlan plan = getPlan(id);
        applyPlan(plan, request);
        plan.setVersion(plan.getVersion() == null ? 2 : plan.getVersion() + 1);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
        replacePlanModels(id, request.modelIds());
        return plan;
    }

    private void applyPlan(BillingPlan plan, SavePlanRequest request) {
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setPriceMinor(request.priceMinor());
        plan.setCurrency(request.currency() == null || request.currency().isBlank() ? "CNY" : request.currency());
        plan.setDurationDays(request.durationDays());
        plan.setQuotaTokens(request.quotaTokens());
        plan.setQuotaCycleDays(request.quotaCycleDays());
        plan.setSort(request.sort() == null ? 0 : request.sort());
    }

    @Override
    @Transactional
    public void replacePlanModels(Long id, List<String> modelIds) {
        getPlan(id);
        planModelMapper.delete(new LambdaQueryWrapper<BillingPlanModel>().eq(BillingPlanModel::getPlanId, id));
        if (modelIds == null) return;
        for (String modelId : modelIds) {
            Long parsed;
            try { parsed = Long.valueOf(modelId); } catch (NumberFormatException ex) {
                throw BusinessException.badRequest("模型 ID 格式不正确");
            }
            ModelConfig model = modelConfigMapper.selectById(parsed);
            if (model == null || !ModelConfig.TYPE_CHAT.equals(model.getModelType())
                    || !model.enabled()) {
                throw BusinessException.badRequest("模型不存在、类型不为 CHAT 或未启用");
            }
            BillingPlanModel link = new BillingPlanModel();
            link.setPlanId(id);
            link.setModelConfigId(parsed);
            link.setCreateTime(LocalDateTime.now());
            planModelMapper.insert(link);
        }
    }

    @Override
    @Transactional
    public void publishPlan(Long id) {
        planMapper.update(null, new LambdaUpdateWrapper<BillingPlan>()
                .eq(BillingPlan::getId, id).set(BillingPlan::getStatus, "PUBLISHED")
                .set(BillingPlan::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void disablePlan(Long id) {
        planMapper.update(null, new LambdaUpdateWrapper<BillingPlan>()
                .eq(BillingPlan::getId, id).set(BillingPlan::getStatus, "DISABLED")
                .set(BillingPlan::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public PageResult<TokenPack> pageTokenPacks(long page, long size) {
        Page<TokenPack> result = packMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TokenPack>().orderByAsc(TokenPack::getSort).orderByDesc(TokenPack::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public TokenPack getTokenPack(Long id) {
        TokenPack pack = packMapper.selectById(id);
        if (pack == null) throw BusinessException.notFound("Token 补充包不存在");
        return pack;
    }

    @Override
    @Transactional
    public TokenPack createTokenPack(SaveTokenPackRequest request) {
        TokenPack pack = new TokenPack();
        applyPack(pack, request);
        pack.setStatus("PUBLISHED");
        pack.setCreateTime(LocalDateTime.now());
        pack.setUpdateTime(LocalDateTime.now());
        packMapper.insert(pack);
        return pack;
    }

    @Override
    @Transactional
    public TokenPack updateTokenPack(Long id, SaveTokenPackRequest request) {
        TokenPack pack = getTokenPack(id);
        applyPack(pack, request);
        pack.setUpdateTime(LocalDateTime.now());
        packMapper.updateById(pack);
        return pack;
    }

    private void applyPack(TokenPack pack, SaveTokenPackRequest request) {
        pack.setCode(request.code());
        pack.setName(request.name());
        pack.setDescription(request.description());
        pack.setPriceMinor(request.priceMinor());
        pack.setCurrency(request.currency() == null || request.currency().isBlank() ? "CNY" : request.currency());
        pack.setTokens(request.tokens());
        pack.setExpireDays(request.expireDays());
        pack.setSort(request.sort() == null ? 0 : request.sort());
    }

    @Override
    @Transactional
    public void disableTokenPack(Long id) {
        packMapper.update(null, new LambdaUpdateWrapper<TokenPack>()
                .eq(TokenPack::getId, id).set(TokenPack::getStatus, "DISABLED")
                .set(TokenPack::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public Map<String, Object> catalog() {
        List<BillingPlan> plans = planMapper.selectList(new LambdaQueryWrapper<BillingPlan>()
                .eq(BillingPlan::getStatus, "PUBLISHED").orderByAsc(BillingPlan::getSort));
        List<TokenPack> packs = packMapper.selectList(new LambdaQueryWrapper<TokenPack>()
                .eq(TokenPack::getStatus, "PUBLISHED").orderByAsc(TokenPack::getSort));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plans", plans);
        result.put("tokenPacks", packs);
        return result;
    }

    @Override
    public Map<String, Object> me(Long userId) {
        BillingSetting setting = getSettings();
        BillingPlan defaultPlan = setting.getDefaultPlanId() == null ? null : planMapper.selectById(setting.getDefaultPlanId());
        UserSubscription subscription = subscriptionMapper.selectOne(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getStatus, "ACTIVE")
                .ge(UserSubscription::getEndTime, LocalDateTime.now())
                .orderByDesc(UserSubscription::getEndTime).last("LIMIT 1"));

        List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                .eq(TokenLot::getUserId, userId)
                .and(w -> w.isNull(TokenLot::getExpireTime).or().ge(TokenLot::getExpireTime, LocalDateTime.now())));
        long available = lots.stream().mapToLong(l -> Math.max(0L, (l.getRemainingTokens() == null ? 0L : l.getRemainingTokens())
                - (l.getHeldTokens() == null ? 0L : l.getHeldTokens()))).sum();
        long held = lots.stream().mapToLong(l -> l.getHeldTokens() == null ? 0L : l.getHeldTokens()).sum();
        long subscriptionTokens = lots.stream().filter(l -> "SUBSCRIPTION".equals(l.getSourceType()))
                .mapToLong(l -> l.getRemainingTokens() == null ? 0L : l.getRemainingTokens()).sum();
        long topUpTokens = available - subscriptionTokens;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultPlan", defaultPlan == null ? null : Map.of("id", String.valueOf(defaultPlan.getId()),
                "name", defaultPlan.getName()));
        result.put("activeSubscription", subscription == null ? null : subscription);
        result.put("balance", Map.of("availableTokens", available, "heldTokens", held,
                "subscriptionTokens", subscriptionTokens, "topUpTokens", topUpTokens));
        result.put("lots", lots);

        Long planId = subscription == null ? setting.getDefaultPlanId() : subscription.getPlanId();
        if (planId != null) {
            List<BillingPlanModel> links = planModelMapper.selectList(new LambdaQueryWrapper<BillingPlanModel>()
                    .eq(BillingPlanModel::getPlanId, planId));
            List<Map<String, Object>> allowed = new ArrayList<>();
            for (BillingPlanModel link : links) {
                ModelConfig model = modelConfigMapper.selectById(link.getModelConfigId());
                if (model != null && model.enabled()) {
                    allowed.add(Map.of("id", String.valueOf(model.getId()), "name",
                            model.getDisplayName() == null ? model.getModelId() : model.getDisplayName()));
                }
            }
            result.put("allowedModels", allowed);
        } else {
            result.put("allowedModels", List.of());
        }
        return result;
    }

    @Override
    public PageResult<TokenLedger> ledger(Long userId, long page, long size) {
        Page<TokenLedger> result = ledgerMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TokenLedger>().eq(TokenLedger::getUserId, userId)
                        .orderByDesc(TokenLedger::getCreatedAt).orderByDesc(TokenLedger::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public PageResult<TokenUsage> usage(Long userId, long page, long size, Long modelId, String status,
                                        String from, String to) {
        LambdaQueryWrapper<TokenUsage> query = new LambdaQueryWrapper<TokenUsage>()
                .eq(TokenUsage::getUserId, userId);
        if (modelId != null) query.eq(TokenUsage::getModelConfigId, modelId);
        if (status != null && !status.isBlank()) query.eq(TokenUsage::getStatus, status);
        if (from != null && !from.isBlank()) query.ge(TokenUsage::getCreatedAt, parseStart(from));
        if (to != null && !to.isBlank()) query.le(TokenUsage::getCreatedAt, parseEnd(to));
        query.orderByDesc(TokenUsage::getCreatedAt).orderByDesc(TokenUsage::getId);
        Page<TokenUsage> result = usageMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public BillingOrder createOrder(Long userId, String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.badRequest("缺少 Idempotency-Key");
        }
        BillingOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<BillingOrder>()
                .eq(BillingOrder::getUserId, userId).eq(BillingOrder::getIdempotencyKey, idempotencyKey));
        if (existing != null) return existing;

        Long productId;
        try { productId = Long.valueOf(request.productId()); } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        BillingOrder order = new BillingOrder();
        order.setUserId(userId);
        order.setProductType(request.productType());
        order.setProductId(productId);
        order.setIdempotencyKey(idempotencyKey);
        order.setPaymentProvider(request.paymentProvider());
        order.setStatus("PENDING_PAYMENT");
        order.setExpireTime(LocalDateTime.now().plusHours(24));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setOrderNo("CB" + System.currentTimeMillis());

        if ("SUBSCRIPTION".equalsIgnoreCase(request.productType())) {
            BillingPlan plan = planMapper.selectById(productId);
            if (plan == null || !"PUBLISHED".equals(plan.getStatus()))
                throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            order.setAmountMinor(plan.getPriceMinor());
            order.setCurrency(plan.getCurrency());
            snapshot.put("name", plan.getName());
            snapshot.put("durationDays", plan.getDurationDays());
            snapshot.put("quotaTokens", plan.getQuotaTokens());
            snapshot.put("version", plan.getVersion());
        } else if ("TOKEN_PACK".equalsIgnoreCase(request.productType())) {
            TokenPack pack = packMapper.selectById(productId);
            if (pack == null || !"PUBLISHED".equals(pack.getStatus()))
                throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            order.setAmountMinor(pack.getPriceMinor());
            order.setCurrency(pack.getCurrency());
            snapshot.put("name", pack.getName());
            snapshot.put("tokens", pack.getTokens());
            snapshot.put("expireDays", pack.getExpireDays());
        } else {
            throw BusinessException.badRequest("不支持的商品类型");
        }
        order.setProductSnapshot(JSON.toJSONString(snapshot));
        orderMapper.insert(order);
        return order;
    }

    @Override
    public BillingOrder getOrder(Long userId, String orderNo) {
        BillingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BillingOrder>()
                .eq(BillingOrder::getOrderNo, orderNo).eq(BillingOrder::getUserId, userId));
        if (order == null) throw BusinessException.notFound("订单不存在");
        return order;
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, String orderNo) {
        BillingOrder order = getOrder(userId, orderNo);
        if (!"PENDING_PAYMENT".equals(order.getStatus()))
            throw new BusinessException(ResultCode.ORDER_STATE_CONFLICT);
        order.setStatus("CANCELED");
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public PageResult<BillingOrder> pageOrders(long page, long size, String status, Long userId) {
        LambdaQueryWrapper<BillingOrder> query = new LambdaQueryWrapper<BillingOrder>();
        if (status != null && !status.isBlank()) query.eq(BillingOrder::getStatus, status);
        if (userId != null) query.eq(BillingOrder::getUserId, userId);
        query.orderByDesc(BillingOrder::getCreateTime).orderByDesc(BillingOrder::getId);
        Page<BillingOrder> result = orderMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public Map<String, Object> usageSummary(String from, String to) {
        LambdaQueryWrapper<TokenUsage> query = new LambdaQueryWrapper<TokenUsage>();
        if (from != null && !from.isBlank()) query.ge(TokenUsage::getCreatedAt, parseStart(from));
        if (to != null && !to.isBlank()) query.le(TokenUsage::getCreatedAt, parseEnd(to));
        List<TokenUsage> rows = usageMapper.selectList(query);
        long input = rows.stream().mapToLong(r -> r.getInputTokens() == null ? 0 : r.getInputTokens()).sum();
        long output = rows.stream().mapToLong(r -> r.getOutputTokens() == null ? 0 : r.getOutputTokens()).sum();
        long charged = rows.stream().mapToLong(r -> r.getChargedTokens() == null ? 0 : r.getChargedTokens()).sum();
        long success = rows.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        long failed = rows.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long duration = rows.stream().mapToLong(r -> r.getDurationMs() == null ? 0 : r.getDurationMs()).sum();
        long users = rows.stream().map(TokenUsage::getUserId).filter(Objects::nonNull).distinct().count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", Map.of("from", from == null ? "" : from, "to", to == null ? "" : to));
        result.put("requestCount", rows.size());
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("activeUserCount", users);
        result.put("inputTokens", input);
        result.put("outputTokens", output);
        result.put("chargedTokens", charged);
        result.put("averageDurationMs", rows.isEmpty() ? 0 : duration / rows.size());
        return result;
    }

    @Override
    public PageResult<Map<String, Object>> usageByModel(long page, long size, String from, String to) {
        LambdaQueryWrapper<TokenUsage> query = new LambdaQueryWrapper<TokenUsage>();
        if (from != null && !from.isBlank()) query.ge(TokenUsage::getCreatedAt, parseStart(from));
        if (to != null && !to.isBlank()) query.le(TokenUsage::getCreatedAt, parseEnd(to));
        List<TokenUsage> rows = usageMapper.selectList(query);
        Map<Long, List<TokenUsage>> grouped = new LinkedHashMap<>();
        for (TokenUsage row : rows) grouped.computeIfAbsent(row.getModelConfigId(), k -> new ArrayList<>()).add(row);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map.Entry<Long, List<TokenUsage>> entry : grouped.entrySet()) {
            List<TokenUsage> list = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelId", entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            item.put("modelName", list.getFirst().getModelNameSnapshot());
            item.put("requestCount", list.size());
            item.put("inputTokens", list.stream().mapToLong(r -> r.getInputTokens() == null ? 0 : r.getInputTokens()).sum());
            item.put("outputTokens", list.stream().mapToLong(r -> r.getOutputTokens() == null ? 0 : r.getOutputTokens()).sum());
            item.put("chargedTokens", list.stream().mapToLong(r -> r.getChargedTokens() == null ? 0 : r.getChargedTokens()).sum());
            item.put("failedCount", list.stream().filter(r -> "FAILED".equals(r.getStatus())).count());
            records.add(item);
        }
        long fromIndex = (page - 1) * size;
        long toIndex = Math.min(records.size(), fromIndex + size);
        List<Map<String, Object>> pageRecords = fromIndex >= records.size() ? List.of()
                : records.subList((int) fromIndex, (int) toIndex);
        return PageResult.of(pageRecords, records.size(), page, size);
    }

    @Override
    public List<Map<String, Object>> usageTrend(String from, String to, String interval) {
        LocalDateTime fromTime = StringUtils.hasText(from) ? parseStart(from) : LocalDateTime.now().minusDays(30);
        LocalDateTime toTime = StringUtils.hasText(to) ? parseEnd(to) : LocalDateTime.now();
        List<TokenUsage> rows = usageMapper.selectList(new LambdaQueryWrapper<TokenUsage>()
                .ge(TokenUsage::getCreatedAt, fromTime).le(TokenUsage::getCreatedAt, toTime)
                .orderByAsc(TokenUsage::getCreatedAt));
        Map<String, List<TokenUsage>> grouped = new LinkedHashMap<>();
        String normalized = interval == null ? "DAY" : interval.toUpperCase(Locale.ROOT);
        for (TokenUsage row : rows) {
            LocalDateTime time = row.getCreatedAt();
            String key;
            if ("HOUR".equals(normalized)) {
                key = time.withMinute(0).withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } else if ("MONTH".equals(normalized)) {
                key = time.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            } else {
                key = time.toLocalDate().toString();
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, List<TokenUsage>> entry : grouped.entrySet()) {
            List<TokenUsage> list = entry.getValue();
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("time", entry.getKey());
            bucket.put("requestCount", list.size());
            bucket.put("successCount", list.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count());
            bucket.put("failedCount", list.stream().filter(r -> "FAILED".equals(r.getStatus())).count());
            bucket.put("inputTokens", list.stream().mapToLong(r -> r.getInputTokens() == null ? 0 : r.getInputTokens()).sum());
            bucket.put("outputTokens", list.stream().mapToLong(r -> r.getOutputTokens() == null ? 0 : r.getOutputTokens()).sum());
            bucket.put("chargedTokens", list.stream().mapToLong(r -> r.getChargedTokens() == null ? 0 : r.getChargedTokens()).sum());
            trend.add(bucket);
        }
        return trend;
    }

    @Override
    @Transactional
    public Map<String, Object> grantTokens(Long userId, String idempotencyKey, TokenGrantRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw BusinessException.badRequest("缺少 Idempotency-Key");
        AdminTokenGrant existing = grantMapper.selectOne(new LambdaQueryWrapper<AdminTokenGrant>()
                .eq(AdminTokenGrant::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return grantResponse(existing);
        }
        Long packId;
        try { packId = Long.valueOf(request.tokenPackId()); } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
        }
        TokenPack pack = packMapper.selectById(packId);
        if (pack == null || !"PUBLISHED".equals(pack.getStatus()))
            throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);

        LocalDateTime expire = pack.getExpireDays() == null ? null : LocalDateTime.now().plusDays(pack.getExpireDays());
        TokenLot lot = new TokenLot();
        lot.setUserId(userId);
        lot.setSourceType("ADMIN_GRANT");
        lot.setSourceId(pack.getId());
        lot.setGrantedTokens(pack.getTokens());
        lot.setRemainingTokens(pack.getTokens());
        lot.setHeldTokens(0L);
        lot.setExpireTime(expire);
        lot.setCreateTime(LocalDateTime.now());
        lot.setUpdateTime(LocalDateTime.now());
        lotMapper.insert(lot);

        long balanceAfter = availableBalance(userId);
        TokenLedger ledger = new TokenLedger();
        ledger.setUserId(userId);
        ledger.setEventType("ADMIN_GRANT");
        ledger.setDeltaTokens(pack.getTokens());
        ledger.setBalanceAfter(balanceAfter);
        ledger.setSourceType("TOKEN_PACK");
        ledger.setSourceId(pack.getId());
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);

        AdminTokenGrant grant = new AdminTokenGrant();
        grant.setGrantNo("TG" + System.currentTimeMillis());
        grant.setUserId(userId);
        grant.setTokenPackId(pack.getId());
        grant.setOperatorId(SecurityUtils.getUserId());
        grant.setTokens(pack.getTokens());
        grant.setReason(request.reason());
        grant.setExpireTime(expire);
        grant.setIdempotencyKey(idempotencyKey);
        grant.setLedgerId(ledger.getId());
        grant.setCreateTime(LocalDateTime.now());
        grantMapper.insert(grant);
        addOutbox("admin-grant:" + grant.getId(), grant.getUserId(),
                "BILLING_TOKEN_PACK_GRANTED", "管理员已发放 Token",
                "管理员发放了 " + pack.getTokens() + " Token。",
                Map.of("grantId", String.valueOf(grant.getId()), "tokens", pack.getTokens(),
                        "reason", request.reason()));
        return grantResponse(grant);
    }

    @Override
    @Transactional
    public Map<String, Object> adjustTokens(Long userId, Map<String, Object> request) {
        Object raw = request.get("deltaTokens");
        long delta;
        try { delta = Long.parseLong(String.valueOf(raw)); } catch (Exception ex) {
            throw BusinessException.badRequest("deltaTokens 必须为整数");
        }
        String reason = String.valueOf(request.getOrDefault("reason", ""));
        if (reason.isBlank()) throw BusinessException.badRequest("必须填写调账原因");

        if (delta > 0) {
            TokenLot lot = new TokenLot();
            lot.setUserId(userId);
            lot.setSourceType("ADMIN_ADJUST");
            lot.setGrantedTokens(delta);
            lot.setRemainingTokens(delta);
            lot.setHeldTokens(0L);
            lot.setCreateTime(LocalDateTime.now());
            lot.setUpdateTime(LocalDateTime.now());
            lotMapper.insert(lot);
        } else if (delta < 0) {
            long remaining = -delta;
            List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                    .eq(TokenLot::getUserId, userId).gt(TokenLot::getRemainingTokens, 0)
                    .orderByAsc(TokenLot::getExpireTime).orderByAsc(TokenLot::getId));
            for (TokenLot lot : lots) {
                if (remaining == 0) break;
                long take = Math.min(remaining, lot.getRemainingTokens());
                lot.setRemainingTokens(lot.getRemainingTokens() - take);
                lot.setUpdateTime(LocalDateTime.now());
                lotMapper.updateById(lot);
                remaining -= take;
            }
            if (remaining > 0) throw new BusinessException(ResultCode.TOKEN_BALANCE_INSUFFICIENT);
        }
        TokenLedger ledger = new TokenLedger();
        ledger.setUserId(userId);
        ledger.setEventType("ADMIN_ADJUST");
        ledger.setDeltaTokens(delta);
        ledger.setBalanceAfter(availableBalance(userId));
        ledger.setSourceType("ADMIN_ADJUST");
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deltaTokens", delta);
        result.put("balanceAfter", ledger.getBalanceAfter());
        result.put("reason", reason);
        result.put("ledgerId", ledger.getId());
        return result;
    }

    @Override
    public Map<String, Object> adminUserBilling(Long userId, String from, String to) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscription", subscriptionMapper.selectList(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId).orderByDesc(UserSubscription::getEndTime)));
        result.put("lots", lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                .eq(TokenLot::getUserId, userId).orderByAsc(TokenLot::getExpireTime)));
        Map<String, Object> usageMap = usageSummaryForUser(userId, from, to);
        result.put("usage", usageMap);
        return result;
    }

    @Override
    @Transactional
    public void initializeNewUser(Long userId) {
        BillingSetting setting = getSettings();
        if (setting.getDefaultPlanId() == null) {
            BillingPlan free = planMapper.selectOne(new LambdaQueryWrapper<BillingPlan>()
                    .eq(BillingPlan::getPlanType, "FREE").eq(BillingPlan::getStatus, "PUBLISHED").last("LIMIT 1"));
            if (free == null) {
                free = new BillingPlan();
                free.setCode("free");
                free.setName("免费版");
                free.setDescription("新用户默认方案");
                free.setPlanType("FREE");
                free.setPriceMinor(0L);
                free.setCurrency(setting.getCurrency() == null ? "CNY" : setting.getCurrency());
                free.setDurationDays(null);
                free.setQuotaTokens(0L);
                free.setQuotaCycleDays(null);
                free.setStatus("PUBLISHED");
                free.setIsDefault(true);
                free.setVersion(1);
                free.setSort(0);
                free.setCreateTime(LocalDateTime.now());
                free.setUpdateTime(LocalDateTime.now());
                planMapper.insert(free);
            }
            planMapper.update(null, new LambdaUpdateWrapper<BillingPlan>()
                    .ne(BillingPlan::getId, free.getId()).set(BillingPlan::getIsDefault, false));
            setting.setDefaultPlanId(free.getId());
            settingMapper.updateById(setting);
        }

        long tokens = setting.getRegistrationTokens() == null ? 0L : setting.getRegistrationTokens();
        if (tokens <= 0) return;
        long count = lotMapper.selectCount(new LambdaQueryWrapper<TokenLot>()
                .eq(TokenLot::getUserId, userId).eq(TokenLot::getSourceType, "REGISTRATION"));
        if (count > 0) return;
        LocalDateTime expire = setting.getRegistrationTokenExpireDays() == null ? null
                : LocalDateTime.now().plusDays(setting.getRegistrationTokenExpireDays());
        TokenLot lot = new TokenLot();
        lot.setUserId(userId);
        lot.setSourceType("REGISTRATION");
        lot.setGrantedTokens(tokens);
        lot.setRemainingTokens(tokens);
        lot.setHeldTokens(0L);
        lot.setExpireTime(expire);
        lot.setCreateTime(LocalDateTime.now());
        lot.setUpdateTime(LocalDateTime.now());
        lotMapper.insert(lot);

        TokenLedger ledger = new TokenLedger();
        ledger.setUserId(userId);
        ledger.setEventType("REGISTRATION_GRANT");
        ledger.setDeltaTokens(tokens);
        ledger.setBalanceAfter(tokens);
        ledger.setSourceType("REGISTRATION");
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
    }

    @Override
    @Transactional
    public void reserveChatTokens(Long userId, String requestId, long estimatedTokens) {
        if (requestId == null || requestId.isBlank()) throw BusinessException.badRequest("缺少 requestId");
        TokenReservation existing = reservationMapper.selectOne(new LambdaQueryWrapper<TokenReservation>()
                .eq(TokenReservation::getRequestId, requestId));
        if (existing != null) return;
        long needed = Math.max(1L, estimatedTokens);
        List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                .eq(TokenLot::getUserId, userId)
                .and(w -> w.isNull(TokenLot::getExpireTime).or().ge(TokenLot::getExpireTime, LocalDateTime.now()))
                .orderByAsc(TokenLot::getExpireTime).orderByAsc(TokenLot::getId));
        long available = lots.stream().mapToLong(l -> Math.max(0L,
                (l.getRemainingTokens() == null ? 0L : l.getRemainingTokens())
                        - (l.getHeldTokens() == null ? 0L : l.getHeldTokens()))).sum();
        if (available < needed) throw new BusinessException(ResultCode.TOKEN_BALANCE_INSUFFICIENT);

        List<Map<String, Object>> snapshot = new ArrayList<>();
        long remaining = needed;
        for (TokenLot lot : lots) {
            if (remaining == 0) break;
            long free = Math.max(0L, lot.getRemainingTokens() - lot.getHeldTokens());
            long take = Math.min(free, remaining);
            if (take <= 0) continue;
            lot.setHeldTokens(lot.getHeldTokens() + take);
            lot.setUpdateTime(LocalDateTime.now());
            lotMapper.updateById(lot);
            snapshot.add(Map.of("lotId", lot.getId(), "tokens", take));
            remaining -= take;
        }
        TokenReservation reservation = new TokenReservation();
        reservation.setRequestId(requestId);
        reservation.setUserId(userId);
        reservation.setReservedTokens(needed);
        reservation.setSettledTokens(0L);
        reservation.setStatus("RESERVED");
        reservation.setLotSnapshot(JSON.toJSONString(snapshot));
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.insert(reservation);
    }

    @Override
    @Transactional
    public void settleChatTokens(Long userId, String requestId, Long modelId, Long providerId,
                                 String modelName, long inputTokens, long outputTokens,
                                 long durationMs, String status, Long conversationId) {
        TokenReservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<TokenReservation>()
                .eq(TokenReservation::getRequestId, requestId));
        if (reservation != null && "SETTLED".equals(reservation.getStatus())) return;
        long charged;
        if ("FAILED".equals(status) && inputTokens == 0 && outputTokens == 0) {
            charged = 0;
        } else {
            long inputRate = 1000000L;
            long outputRate = 1000000L;
            if (modelId != null) {
                ModelBillingRate rate = billingRateMapper.selectOne(new LambdaQueryWrapper<ModelBillingRate>()
                        .eq(ModelBillingRate::getModelConfigId, modelId));
                if (rate != null) {
                    inputRate = rate.getInputCreditsPerMillion();
                    outputRate = rate.getOutputCreditsPerMillion();
                }
            }
            long numerator = inputTokens * inputRate + outputTokens * outputRate;
            charged = (numerator + 999999L) / 1000000L;
        }
        if (reservation != null) {
            List<Map> snapshot = JSON.parseArray(reservation.getLotSnapshot(), Map.class);
            long remainingCharge = charged;
            long heldTotal = 0;
            for (Map part : snapshot) {
                Long lotId = Long.valueOf(String.valueOf(part.get("lotId")));
                long held = Long.parseLong(String.valueOf(part.get("tokens")));
                heldTotal += held;
                TokenLot lot = lotMapper.selectById(lotId);
                if (lot == null) continue;
                long take = Math.min(held, remainingCharge);
                lot.setRemainingTokens(Math.max(0L, lot.getRemainingTokens() - take));
                lot.setHeldTokens(Math.max(0L, lot.getHeldTokens() - held));
                lot.setUpdateTime(LocalDateTime.now());
                lotMapper.updateById(lot);
                remainingCharge -= take;
            }
            if (remainingCharge > 0) {
                List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                        .eq(TokenLot::getUserId, userId).gt(TokenLot::getRemainingTokens, 0)
                        .orderByAsc(TokenLot::getExpireTime).orderByAsc(TokenLot::getId));
                for (TokenLot lot : lots) {
                    if (remainingCharge == 0) break;
                    long take = Math.min(lot.getRemainingTokens(), remainingCharge);
                    lot.setRemainingTokens(lot.getRemainingTokens() - take);
                    lot.setUpdateTime(LocalDateTime.now());
                    lotMapper.updateById(lot);
                    remainingCharge -= take;
                }
            }
            reservation.setSettledTokens(charged);
            reservation.setStatus("SETTLED");
            reservation.setUpdateTime(LocalDateTime.now());
            reservationMapper.updateById(reservation);
        }

        TokenLedger ledger = new TokenLedger();
        ledger.setUserId(userId);
        ledger.setEventType("CHAT_CHARGE");
        ledger.setDeltaTokens(-charged);
        ledger.setBalanceAfter(availableBalance(userId));
        ledger.setSourceType("CHAT");
        ledger.setRequestId(requestId);
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);

        TokenUsage usage = new TokenUsage();
        usage.setRequestId(requestId);
        usage.setUserId(userId);
        usage.setModelConfigId(modelId);
        usage.setProviderId(providerId);
        usage.setModelNameSnapshot(modelName);
        usage.setInputTokens(inputTokens);
        usage.setOutputTokens(outputTokens);
        usage.setChargedTokens(charged);
        usage.setDurationMs(durationMs);
        usage.setStatus(status == null ? "FAILED" : status);
        usage.setConversationId(conversationId);
        usage.setCreatedAt(LocalDateTime.now());
        usageMapper.insert(usage);
    }

    @Override
    @Transactional
    public void releaseChatReservation(Long userId, String requestId) {
        settleChatTokens(userId, requestId, null, null, null, 0, 0, 0, "FAILED", null);
    }

    @Override
    @Transactional
    public boolean processPaymentWebhook(String provider, String eventId, String transactionId,
                                         String orderNo, long amountMinor, String currency,
                                         String payloadHash, String rawPayload) {
        PaymentWebhookEvent event = webhookEventMapper.selectOne(new LambdaQueryWrapper<PaymentWebhookEvent>()
                .eq(PaymentWebhookEvent::getProvider, provider)
                .eq(PaymentWebhookEvent::getProviderEventId, eventId));
        if (event != null) return true;
        BillingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BillingOrder>()
                .eq(BillingOrder::getOrderNo, orderNo));
        if (order == null) throw BusinessException.notFound("支付订单不存在");
        if (!order.getAmountMinor().equals(amountMinor) || !order.getCurrency().equalsIgnoreCase(currency))
            throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH);

        event = new PaymentWebhookEvent();
        event.setProvider(provider);
        event.setProviderEventId(eventId);
        event.setPayloadHash(payloadHash);
        event.setProcessedTime(LocalDateTime.now());
        event.setResult("PROCESSED");
        event.setCreateTime(LocalDateTime.now());
        webhookEventMapper.insert(event);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(order.getId());
        transaction.setProvider(provider);
        transaction.setProviderTransactionId(transactionId);
        transaction.setAmountMinor(amountMinor);
        transaction.setCurrency(currency);
        transaction.setStatus("PAID");
        transaction.setPaidTime(LocalDateTime.now());
        transaction.setRawPayload(rawPayload);
        transaction.setCreateTime(LocalDateTime.now());
        paymentTransactionMapper.insert(transaction);

        if (!"PENDING_PAYMENT".equals(order.getStatus())) return true;
        order.setStatus("PAID");
        order.setPaidTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        Map<String, Object> snapshot = JSON.parseObject(order.getProductSnapshot());
        if ("SUBSCRIPTION".equalsIgnoreCase(order.getProductType())) {
            BillingPlan plan = planMapper.selectById(order.getProductId());
            if (plan == null) throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            LocalDateTime start = LocalDateTime.now();
            LocalDateTime end = start.plusDays(plan.getDurationDays());
            UserSubscription subscription = new UserSubscription();
            subscription.setUserId(order.getUserId());
            subscription.setPlanId(plan.getId());
            subscription.setStatus("ACTIVE");
            subscription.setStartTime(start);
            subscription.setEndTime(end);
            subscription.setNextQuotaTime(start.plusDays(plan.getQuotaCycleDays()));
            subscription.setProductSnapshot(order.getProductSnapshot());
            subscription.setCreateTime(LocalDateTime.now());
            subscription.setUpdateTime(LocalDateTime.now());
            subscriptionMapper.insert(subscription);
            if (plan.getQuotaTokens() != null && plan.getQuotaTokens() > 0) {
                grantLot(order.getUserId(), "SUBSCRIPTION", plan.getId(), plan.getQuotaTokens(),
                        subscription.getNextQuotaTime(), "SUBSCRIPTION_GRANT");
            }
            addOutbox("subscription:" + order.getOrderNo(), order.getUserId(),
                    "BILLING_SUBSCRIPTION_ACTIVATED", "订阅已开通",
                    "方案「" + plan.getName() + "」已开通。", Map.of("orderNo", order.getOrderNo(),
                            "planId", String.valueOf(plan.getId()), "endTime", end.toString()));
        } else {
            TokenPack pack = packMapper.selectById(order.getProductId());
            if (pack == null) throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            LocalDateTime expire = pack.getExpireDays() == null ? null
                    : LocalDateTime.now().plusDays(pack.getExpireDays());
            grantLot(order.getUserId(), "TOKEN_PACK", pack.getId(), pack.getTokens(), expire, "TOKEN_PACK_PURCHASE");
            addOutbox("pack:" + order.getOrderNo(), order.getUserId(),
                    "BILLING_TOKEN_PACK_PURCHASED", "Token 补充包购买成功",
                    "已增加 " + pack.getTokens() + " Token。", Map.of("orderNo", order.getOrderNo(),
                            "tokens", pack.getTokens()));
        }
        return true;
    }

    private void grantLot(Long userId, String sourceType, Long sourceId, long tokens,
                          LocalDateTime expire, String eventType) {
        TokenLot lot = new TokenLot();
        lot.setUserId(userId);
        lot.setSourceType(sourceType);
        lot.setSourceId(sourceId);
        lot.setGrantedTokens(tokens);
        lot.setRemainingTokens(tokens);
        lot.setHeldTokens(0L);
        lot.setExpireTime(expire);
        lot.setCreateTime(LocalDateTime.now());
        lot.setUpdateTime(LocalDateTime.now());
        lotMapper.insert(lot);
        TokenLedger ledger = new TokenLedger();
        ledger.setUserId(userId);
        ledger.setEventType(eventType);
        ledger.setDeltaTokens(tokens);
        ledger.setBalanceAfter(availableBalance(userId));
        ledger.setSourceType(sourceType);
        ledger.setSourceId(sourceId);
        ledger.setCreatedAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
    }

    private void addOutbox(String eventKey, Long userId, String type, String title, String content,
                           Map<String, Object> payload) {
        BillingNotificationOutbox outbox = new BillingNotificationOutbox();
        outbox.setEventKey(eventKey);
        outbox.setUserId(userId);
        outbox.setType(type);
        outbox.setTitle(title);
        outbox.setContent(content);
        outbox.setPayloadJson(JSON.toJSONString(payload));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextAttemptTime(LocalDateTime.now());
        outbox.setCreateTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void dispatchBillingNotifications() {
        List<BillingNotificationOutbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<BillingNotificationOutbox>()
                .eq(BillingNotificationOutbox::getStatus, "PENDING")
                .le(BillingNotificationOutbox::getNextAttemptTime, LocalDateTime.now())
                .orderByAsc(BillingNotificationOutbox::getId).last("LIMIT 50"));
        for (BillingNotificationOutbox outbox : pending) {
            try {
                Map<String, Object> payload = JSON.parseObject(outbox.getPayloadJson());
                notificationService.notify(outbox.getUserId(), outbox.getEventKey(), outbox.getType(),
                        outbox.getTitle(), outbox.getContent(), payload);
                outbox.setStatus("PROCESSED");
                outbox.setProcessedTime(LocalDateTime.now());
                outboxMapper.updateById(outbox);
            } catch (Exception ex) {
                int attempts = outbox.getAttempts() == null ? 0 : outbox.getAttempts();
                outbox.setAttempts(attempts + 1);
                outbox.setNextAttemptTime(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(attempts, 6))));
                outbox.setLastError(ex.getMessage());
                outboxMapper.updateById(outbox);
            }
        }
    }

    private Map<String, Object> usageSummaryForUser(Long userId, String from, String to) {
        LambdaQueryWrapper<TokenUsage> query = new LambdaQueryWrapper<TokenUsage>().eq(TokenUsage::getUserId, userId);
        if (from != null && !from.isBlank()) query.ge(TokenUsage::getCreatedAt, parseStart(from));
        if (to != null && !to.isBlank()) query.le(TokenUsage::getCreatedAt, parseEnd(to));
        List<TokenUsage> rows = usageMapper.selectList(query);
        return Map.of("requestCount", rows.size(),
                "inputTokens", rows.stream().mapToLong(r -> r.getInputTokens() == null ? 0 : r.getInputTokens()).sum(),
                "outputTokens", rows.stream().mapToLong(r -> r.getOutputTokens() == null ? 0 : r.getOutputTokens()).sum(),
                "chargedTokens", rows.stream().mapToLong(r -> r.getChargedTokens() == null ? 0 : r.getChargedTokens()).sum());
    }

    private Map<String, Object> grantResponse(AdminTokenGrant grant) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(grant.getId()));
        result.put("grantNo", grant.getGrantNo());
        result.put("tokens", grant.getTokens());
        result.put("expireTime", grant.getExpireTime());
        result.put("balanceAfter", availableBalance(grant.getUserId()));
        return result;
    }

    private long availableBalance(Long userId) {
        List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>().eq(TokenLot::getUserId, userId)
                .and(w -> w.isNull(TokenLot::getExpireTime).or().ge(TokenLot::getExpireTime, LocalDateTime.now())));
        return lots.stream().mapToLong(l -> Math.max(0L,
                (l.getRemainingTokens() == null ? 0L : l.getRemainingTokens())
                        - (l.getHeldTokens() == null ? 0L : l.getHeldTokens()))).sum();
    }

    private LocalDateTime parseStart(String value) {
        try { return LocalDateTime.parse(value + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
        catch (Exception ex) { throw BusinessException.badRequest("时间格式不正确，应为 YYYY-MM-DD"); }
    }

    private LocalDateTime parseEnd(String value) {
        try { return LocalDateTime.parse(value + "T23:59:59", DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
        catch (Exception ex) { throw BusinessException.badRequest("时间格式不正确，应为 YYYY-MM-DD"); }
    }
}
