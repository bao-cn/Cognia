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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
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
    private final WalletAccountMapper walletAccountMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final RedeemCodeBatchMapper redeemCodeBatchMapper;
    private final RedeemCodeMapper redeemCodeMapper;
    private final BillingRedemptionMapper billingRedemptionMapper;
    private final StringRedisTemplate redis;
    private final NotificationService notificationService;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REDEEM_USER_HOURLY_LIMIT = 20;
    private static final int REDEEM_IP_HOURLY_LIMIT = 60;

    @Override
    public boolean isBillingEnabled() {
        return !Boolean.FALSE.equals(getSettings().getBillingEnabled());
    }

    @Override
    public BillingSetting getSettings() {
        BillingSetting setting = settingMapper.selectById(1L);
        if (setting == null) {
            setting = new BillingSetting();
            setting.setId(1L);
            setting.setBillingEnabled(true);
            setting.setRegistrationTokens(0L);
            setting.setCurrency("CNY");
            setting.setWalletCurrency("CNY");
            setting.setWalletFractionalDigits(2);
            setting.setWalletUnitLabel("分");
            settingMapper.insert(setting);
        }
        return setting;
    }

    @Override
    @Transactional
    public BillingSetting updateSettings(BillingSettingsRequest request) {
        getSettings();
        BillingSetting setting = settingMapper.selectForUpdate(1L);
        if (setting == null) throw new IllegalStateException("计费设置锁定失败");
        if (request.billingEnabled() != null) setting.setBillingEnabled(request.billingEnabled());
        if (request.defaultPlanId() != null && !request.defaultPlanId().isBlank()) {
            Long planId = Long.valueOf(request.defaultPlanId());
            BillingPlan plan = planMapper.selectById(planId);
            if (plan == null) throw BusinessException.notFound("默认方案不存在");
            setting.setDefaultPlanId(planId);
        }
        setting.setRegistrationTokens(request.registrationTokens());
        setting.setRegistrationTokenExpireDays(request.registrationTokenExpireDays());
        setting.setCurrency(request.currency() == null || request.currency().isBlank() ? "CNY" : request.currency());
        String walletCurrency = request.walletCurrency() == null || request.walletCurrency().isBlank()
                ? (setting.getWalletCurrency() == null ? "CNY" : setting.getWalletCurrency())
                : request.walletCurrency().trim().toUpperCase(Locale.ROOT);
        int walletFractionalDigits = request.walletFractionalDigits() == null
                ? (setting.getWalletFractionalDigits() == null ? 2 : setting.getWalletFractionalDigits())
                : request.walletFractionalDigits();
        String walletUnitLabel = request.walletUnitLabel() == null || request.walletUnitLabel().isBlank()
                ? (setting.getWalletUnitLabel() == null ? "分" : setting.getWalletUnitLabel().trim())
                : request.walletUnitLabel().trim();
        if (walletFractionalDigits < 0 || walletFractionalDigits > 9)
            throw BusinessException.badRequest("钱包小数位数必须在 0 到 9 之间");
        try {
            if (Currency.getInstance(walletCurrency).getDefaultFractionDigits() < 0)
                throw BusinessException.badRequest("钱包币种必须为有效的 ISO 4217 代码");
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("钱包币种必须为有效的 ISO 4217 代码");
        }
        boolean walletUnitChanged = !Objects.equals(setting.getWalletCurrency(), walletCurrency)
                || !Objects.equals(setting.getWalletFractionalDigits(), walletFractionalDigits)
                || !Objects.equals(setting.getWalletUnitLabel(), walletUnitLabel);
        if (walletUnitChanged && (walletAccountMapper.selectCount(null) > 0
                || walletLedgerMapper.selectCount(null) > 0
                || redeemCodeBatchMapper.selectCount(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getBenefitType, "WALLET_CREDIT")) > 0)) {
            throw BusinessException.badRequest("钱包已产生账户、流水或余额兑换码，不能修改币种与单位");
        }
        setting.setWalletCurrency(walletCurrency);
        setting.setWalletFractionalDigits(walletFractionalDigits);
        setting.setWalletUnitLabel(walletUnitLabel);
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
    public List<String> planModelIds(Long id) {
        getPlan(id);
        return planModelMapper.selectList(new LambdaQueryWrapper<BillingPlanModel>()
                        .eq(BillingPlanModel::getPlanId, id))
                .stream().map(link -> String.valueOf(link.getModelConfigId())).toList();
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
    @Transactional
    public void publishTokenPack(Long id) {
        getTokenPack(id);
        packMapper.update(null, new LambdaUpdateWrapper<TokenPack>()
                .eq(TokenPack::getId, id).set(TokenPack::getStatus, "PUBLISHED")
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
        result.put("walletUnit", walletUnitConfig(getSettings()));
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
        result.put("wallet", wallet(userId));
        result.put("lots", lots);

        Long planId = subscription == null ? setting.getDefaultPlanId() : subscription.getPlanId();
        if (planId != null) {
            List<Long> allowedModelIds = null;
            if (subscription != null && StringUtils.hasText(subscription.getProductSnapshot())) {
                Map<String, Object> snapshot = JSON.parseObject(subscription.getProductSnapshot());
                Object rawModelIds = snapshot.get("modelIds");
                if (rawModelIds instanceof Collection<?> modelIds) {
                    allowedModelIds = modelIds.stream().map(id -> valueLong(id, null))
                            .filter(Objects::nonNull).toList();
                }
            }
            List<Long> modelIds = allowedModelIds;
            List<Long> resolvedModelIds = modelIds == null
                    ? planModelMapper.selectList(new LambdaQueryWrapper<BillingPlanModel>()
                            .eq(BillingPlanModel::getPlanId, planId)).stream()
                            .map(BillingPlanModel::getModelConfigId).toList()
                    : modelIds;
            List<Map<String, Object>> allowed = new ArrayList<>();
            for (Long modelId : resolvedModelIds) {
                ModelConfig model = modelConfigMapper.selectById(modelId);
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
    public Map<String, Object> wallet(Long userId) {
        WalletAccount account = walletAccountMapper.selectById(userId);
        BillingSetting setting = getSettings();
        Map<String, Object> result = walletUnitConfig(setting);
        result.put("balanceUnits", account == null || account.getBalanceUnits() == null
                ? 0L : account.getBalanceUnits());
        return result;
    }

    @Override
    public PageResult<WalletLedger> walletLedger(Long userId, long page, long size) {
        Page<WalletLedger> result = walletLedgerMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WalletLedger>().eq(WalletLedger::getUserId, userId)
                        .orderByDesc(WalletLedger::getCreatedAt).orderByDesc(WalletLedger::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public Map<String, Object> adjustWallet(Long userId, String idempotencyKey,
                                            WalletAdjustmentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128)
            throw BusinessException.badRequest("Idempotency-Key 必须为 1 到 128 个字符");
        if (request.deltaUnits() == 0) throw BusinessException.badRequest("钱包调整金额不能为 0");
        String requestId = walletRequestId("ADMIN_ADJUST", userId, idempotencyKey);
        WalletLedger existing = walletLedgerMapper.selectOne(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getRequestId, requestId));
        if (existing != null) return walletAdjustmentResponse(existing);
        WalletLedger entry = applyWalletDelta(userId, request.deltaUnits(), "ADMIN_ADJUST",
                "ADMIN_ADJUST", null, requestId, request.reason().trim());
        return walletAdjustmentResponse(entry);
    }

    @Override
    @Transactional
    public Map<String, Object> redeemCode(Long userId, String idempotencyKey,
                                          RedeemCodeRequest request, String clientIp) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128)
            throw BusinessException.badRequest("Idempotency-Key 必须为 1 到 128 个字符");
        checkRedeemRateLimit(userId, clientIp);
        String normalizedCode = normalizeRedeemCode(request.code());
        String codeHash = sha256(normalizedCode);
        BillingRedemption existing = billingRedemptionMapper.selectOne(new LambdaQueryWrapper<BillingRedemption>()
                .eq(BillingRedemption::getUserId, userId)
                .eq(BillingRedemption::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            RedeemCode priorCode = redeemCodeMapper.selectById(existing.getCodeId());
            if (priorCode == null || !MessageDigest.isEqual(
                    priorCode.getCodeHash().getBytes(StandardCharsets.UTF_8),
                    codeHash.getBytes(StandardCharsets.UTF_8))) {
                throw BusinessException.badRequest("该 Idempotency-Key 已用于其他兑换码");
            }
            return redemptionResponse(existing);
        }
        if (!isBillingEnabled()) throw BusinessException.badRequest("计费功能已关闭");

        RedeemCode candidate = redeemCodeMapper.selectOne(new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getCodeHash, codeHash));
        if (candidate == null) throw BusinessException.badRequest("兑换码无效或已过期");
        RedeemCodeBatch batch = redeemCodeBatchMapper.selectOne(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getId, candidate.getBatchId()).last("FOR UPDATE"));
        RedeemCode code = redeemCodeMapper.selectForUpdateByHash(codeHash);
        LocalDateTime now = LocalDateTime.now();
        BillingRedemption concurrentRedemption = billingRedemptionMapper.selectOne(
                new LambdaQueryWrapper<BillingRedemption>().eq(BillingRedemption::getUserId, userId)
                        .eq(BillingRedemption::getIdempotencyKey, idempotencyKey));
        if (concurrentRedemption != null) {
            if (code != null && Objects.equals(concurrentRedemption.getCodeId(), code.getId())) {
                return redemptionResponse(concurrentRedemption);
            }
            throw new BusinessException(ResultCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (batch == null || code == null || !"ACTIVE".equals(batch.getStatus())
                || !"AVAILABLE".equals(code.getStatus())
                || (batch.getValidFrom() != null && now.isBefore(batch.getValidFrom()))
                || (batch.getExpireTime() != null && !now.isBefore(batch.getExpireTime()))) {
            throw BusinessException.badRequest("兑换码无效或已过期");
        }

        BillingRedemption redemption = new BillingRedemption();
        redemption.setUserId(userId);
        redemption.setCodeId(code.getId());
        redemption.setIdempotencyKey(idempotencyKey);
        redemption.setBenefitType(batch.getBenefitType());
        redemption.setBenefitSnapshot(batch.getBenefitSnapshot());
        redemption.setRedeemedAt(now);
        billingRedemptionMapper.insert(redemption);

        Map<String, Object> benefit = JSON.parseObject(batch.getBenefitSnapshot());
        String reference = "redemption:" + redemption.getId();
        if ("SUBSCRIPTION".equals(batch.getBenefitType())) {
            activateSubscription(userId, batch.getProductId(), benefit, reference,
                    "BILLING_SUBSCRIPTION_REDEEMED", "订阅兑换成功", reference);
        } else if ("TOKEN_PACK".equals(batch.getBenefitType())) {
            grantTokenPack(userId, batch.getProductId(), benefit, reference,
                    "TOKEN_PACK_REDEMPTION", "BILLING_TOKEN_PACK_REDEEMED", reference);
        } else if ("WALLET_CREDIT".equals(batch.getBenefitType())) {
            long amountUnits = valueLong(benefit.get("amountUnits"), 0L);
            if (amountUnits <= 0) throw BusinessException.badRequest("余额兑换码权益配置无效");
            applyWalletDelta(userId, amountUnits, "REDEMPTION_CREDIT", "REDEMPTION",
                    redemption.getId(), reference, "兑换码余额到账");
            addOutbox(reference, userId, "BILLING_WALLET_CREDIT_REDEEMED", "钱包余额已到账",
                    "兑换码已充值至钱包。", Map.of("redemptionId", String.valueOf(redemption.getId()),
                            "amountUnits", amountUnits));
        } else {
            throw BusinessException.badRequest("不支持的兑换码权益类型");
        }

        code.setStatus("REDEEMED");
        code.setRedeemedBy(userId);
        code.setRedeemedAt(now);
        redeemCodeMapper.updateById(code);
        return redemptionResponse(redemption);
    }

    @Override
    public PageResult<Map<String, Object>> pageRedeemCodeBatches(long page, long size) {
        Page<RedeemCodeBatch> result = redeemCodeBatchMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RedeemCodeBatch>().orderByDesc(RedeemCodeBatch::getCreateTime)
                        .orderByDesc(RedeemCodeBatch::getId));
        List<Map<String, Object>> records = new ArrayList<>();
        for (RedeemCodeBatch batch : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(batch.getId()));
            item.put("batchNo", batch.getBatchNo());
            item.put("benefitType", batch.getBenefitType());
            item.put("productId", batch.getProductId() == null ? null : String.valueOf(batch.getProductId()));
            item.put("quantity", batch.getQuantity());
            item.put("redeemedCount", redeemCodeMapper.selectCount(new LambdaQueryWrapper<RedeemCode>()
                    .eq(RedeemCode::getBatchId, batch.getId()).eq(RedeemCode::getStatus, "REDEEMED")));
            item.put("validFrom", batch.getValidFrom());
            item.put("expireTime", batch.getExpireTime());
            item.put("status", batch.getStatus());
            item.put("createTime", batch.getCreateTime());
            records.add(item);
        }
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    public PageResult<Map<String, Object>> pageRedemptions(Long userId, long page, long size) {
        Page<BillingRedemption> result = billingRedemptionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BillingRedemption>().eq(BillingRedemption::getUserId, userId)
                        .orderByDesc(BillingRedemption::getRedeemedAt).orderByDesc(BillingRedemption::getId));
        List<Map<String, Object>> records = result.getRecords().stream().map(redemption -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(redemption.getId()));
            item.put("benefitType", redemption.getBenefitType());
            item.put("benefit", JSON.parseObject(redemption.getBenefitSnapshot()));
            item.put("redeemedAt", redemption.getRedeemedAt());
            return item;
        }).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public Map<String, Object> createRedeemCodeBatch(CreateRedeemCodeBatchRequest request) {
        if (request.quantity() < 1 || request.quantity() > 1000)
            throw BusinessException.badRequest("兑换码数量必须在 1 到 1000 之间");
        LocalDateTime now = LocalDateTime.now();
        if (request.validFrom() != null && request.expireTime() != null
                && !request.expireTime().isAfter(request.validFrom())) {
            throw BusinessException.badRequest("兑换码到期时间必须晚于生效时间");
        }
        Long productId = null;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        String benefitType = request.benefitType().trim().toUpperCase(Locale.ROOT);
        if ("SUBSCRIPTION".equals(benefitType)) {
            productId = parseProductId(request.productId());
            BillingPlan plan = planMapper.selectById(productId);
            if (plan == null || !"PUBLISHED".equals(plan.getStatus()))
                throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            snapshot.put("name", plan.getName());
            snapshot.put("planId", plan.getId());
            snapshot.put("durationDays", plan.getDurationDays());
            snapshot.put("quotaTokens", plan.getQuotaTokens());
            snapshot.put("quotaCycleDays", plan.getQuotaCycleDays());
            snapshot.put("priceMinor", plan.getPriceMinor());
            snapshot.put("currency", plan.getCurrency());
            snapshot.put("version", plan.getVersion());
            snapshot.put("modelIds", planModelIds(plan.getId()));
        } else if ("TOKEN_PACK".equals(benefitType)) {
            productId = parseProductId(request.productId());
            TokenPack pack = packMapper.selectById(productId);
            if (pack == null || !"PUBLISHED".equals(pack.getStatus()))
                throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            snapshot.put("name", pack.getName());
            snapshot.put("tokenPackId", pack.getId());
            snapshot.put("tokens", pack.getTokens());
            snapshot.put("expireDays", pack.getExpireDays());
            snapshot.put("priceMinor", pack.getPriceMinor());
            snapshot.put("currency", pack.getCurrency());
        } else if ("WALLET_CREDIT".equals(benefitType)) {
            if (request.amountUnits() == null || request.amountUnits() <= 0)
                throw BusinessException.badRequest("余额兑换码金额必须大于 0");
            if (BigInteger.valueOf(request.amountUnits()).multiply(BigInteger.valueOf(request.quantity()))
                    .compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw BusinessException.badRequest("该批次余额兑换码总金额超出支持范围");
            }
            snapshot.put("amountUnits", request.amountUnits());
            BillingSetting setting = settingMapper.selectForUpdate(1L);
            if (setting == null) throw new IllegalStateException("计费设置锁定失败");
            snapshot.putAll(walletUnitConfig(setting));
        } else {
            throw BusinessException.badRequest("不支持的兑换码权益类型");
        }

        RedeemCodeBatch batch = new RedeemCodeBatch();
        batch.setBatchNo("RC" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT));
        batch.setBenefitType(benefitType);
        batch.setProductId(productId);
        batch.setBenefitSnapshot(JSON.toJSONString(snapshot));
        batch.setQuantity(request.quantity());
        batch.setCreatedBy(SecurityUtils.getUserId());
        batch.setValidFrom(request.validFrom());
        batch.setExpireTime(request.expireTime());
        batch.setStatus("ACTIVE");
        batch.setCreateTime(now);
        batch.setUpdateTime(now);
        redeemCodeBatchMapper.insert(batch);

        List<String> plaintextCodes = new ArrayList<>(request.quantity());
        for (int i = 0; i < request.quantity(); i++) {
            String plaintext = generateRedeemCode();
            RedeemCode code = new RedeemCode();
            code.setBatchId(batch.getId());
            code.setCodeHash(sha256(normalizeRedeemCode(plaintext)));
            code.setStatus("AVAILABLE");
            code.setCreateTime(now);
            redeemCodeMapper.insert(code);
            plaintextCodes.add(plaintext);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(batch.getId()));
        result.put("batchNo", batch.getBatchNo());
        result.put("benefitType", benefitType);
        result.put("quantity", request.quantity());
        result.put("codes", plaintextCodes);
        return result;
    }

    @Override
    @Transactional
    public void revokeRedeemCodeBatch(Long batchId) {
        RedeemCodeBatch batch = redeemCodeBatchMapper.selectOne(new LambdaQueryWrapper<RedeemCodeBatch>()
                .eq(RedeemCodeBatch::getId, batchId).last("FOR UPDATE"));
        if (batch == null) throw BusinessException.notFound("兑换码批次不存在");
        if (!"ACTIVE".equals(batch.getStatus())) return;
        batch.setStatus("REVOKED");
        batch.setUpdateTime(LocalDateTime.now());
        redeemCodeBatchMapper.updateById(batch);
        redeemCodeMapper.update(null, new LambdaUpdateWrapper<RedeemCode>()
                .eq(RedeemCode::getBatchId, batchId).eq(RedeemCode::getStatus, "AVAILABLE")
                .set(RedeemCode::getStatus, "REVOKED"));
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
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw BusinessException.badRequest("Idempotency-Key 必须为 1 到 128 个字符");
        }
        if (!StringUtils.hasText(request.productType()) || !StringUtils.hasText(request.paymentProvider()))
            throw BusinessException.badRequest("商品类型和支付方式不能为空");
        String productType = request.productType().trim().toUpperCase(Locale.ROOT);
        String paymentProvider = request.paymentProvider().trim().toUpperCase(Locale.ROOT);
        Long productId = parseProductId(request.productId());
        BillingOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<BillingOrder>()
                .eq(BillingOrder::getUserId, userId).eq(BillingOrder::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            if (!existing.getProductType().equalsIgnoreCase(productType)
                    || !Objects.equals(existing.getProductId(), productId)
                    || existing.getPaymentProvider() == null
                    || !existing.getPaymentProvider().equalsIgnoreCase(paymentProvider)) {
                throw new BusinessException(ResultCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return existing;
        }
        if (!isBillingEnabled()) throw BusinessException.badRequest("计费功能已关闭");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        BillingOrder order = new BillingOrder();
        order.setUserId(userId);
        order.setProductType(productType);
        order.setProductId(productId);
        order.setIdempotencyKey(idempotencyKey);
        order.setPaymentProvider(paymentProvider);
        order.setStatus("PENDING_PAYMENT");
        order.setExpireTime(LocalDateTime.now().plusHours(24));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setOrderNo("CB" + System.currentTimeMillis());

        if ("SUBSCRIPTION".equals(productType)) {
            BillingPlan plan = planMapper.selectById(productId);
            if (plan == null || !"PUBLISHED".equals(plan.getStatus()))
                throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            order.setAmountMinor(plan.getPriceMinor());
            order.setCurrency(plan.getCurrency());
            snapshot.put("name", plan.getName());
            snapshot.put("durationDays", plan.getDurationDays());
            snapshot.put("quotaTokens", plan.getQuotaTokens());
            snapshot.put("quotaCycleDays", plan.getQuotaCycleDays());
            snapshot.put("planId", plan.getId());
            snapshot.put("modelIds", planModelIds(plan.getId()));
            snapshot.put("version", plan.getVersion());
        } else if ("TOKEN_PACK".equals(productType)) {
            TokenPack pack = packMapper.selectById(productId);
            if (pack == null || !"PUBLISHED".equals(pack.getStatus()))
                throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
            order.setAmountMinor(pack.getPriceMinor());
            order.setCurrency(pack.getCurrency());
            snapshot.put("name", pack.getName());
            snapshot.put("tokens", pack.getTokens());
            snapshot.put("expireDays", pack.getExpireDays());
            snapshot.put("tokenPackId", pack.getId());
        } else {
            throw BusinessException.badRequest("不支持的商品类型");
        }
        order.setProductSnapshot(JSON.toJSONString(snapshot));
        orderMapper.insert(order);
        if ("WALLET".equals(paymentProvider)) {
            payOrderFromWallet(order);
        }
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
    public void reserveChatTokens(Long userId, String requestId, Long modelId,
                                  long estimatedInputTokens, long estimatedOutputTokens) {
        if (requestId == null || requestId.isBlank()) throw BusinessException.badRequest("缺少 requestId");
        TokenReservation existing = reservationMapper.selectOne(new LambdaQueryWrapper<TokenReservation>()
                .eq(TokenReservation::getRequestId, requestId));
        if (existing != null) return;
        if (!isBillingEnabled()) {
            TokenReservation reservation = new TokenReservation();
            reservation.setRequestId(requestId);
            reservation.setUserId(userId);
            reservation.setReservedTokens(0L);
            reservation.setSettledTokens(0L);
            reservation.setStatus("BYPASSED");
            reservation.setLotSnapshot("[]");
            reservation.setCreateTime(LocalDateTime.now());
            reservation.setUpdateTime(LocalDateTime.now());
            reservationMapper.insert(reservation);
            return;
        }
        ModelBillingRate rate = billingRate(modelId);
        long needed = "ROUND".equals(rate.getBillingMode())
                ? Math.max(0L, rate.getCreditsPerRound() == null ? 0L : rate.getCreditsPerRound())
                : tokenCharge(estimatedInputTokens, estimatedOutputTokens, rate);
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
        if (!isBillingEnabled() || (reservation != null && "BYPASSED".equals(reservation.getStatus()))) {
            charged = 0;
        } else if ("FAILED".equals(status) && inputTokens == 0 && outputTokens == 0) {
            charged = 0;
        } else {
            ModelBillingRate rate = billingRate(modelId);
            charged = "ROUND".equals(rate.getBillingMode())
                    ? Math.max(0L, rate.getCreditsPerRound() == null ? 0L : rate.getCreditsPerRound())
                    : tokenCharge(inputTokens, outputTokens, rate);
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

    private ModelBillingRate billingRate(Long modelId) {
        if (modelId != null) {
            ModelBillingRate rate = billingRateMapper.selectOne(new LambdaQueryWrapper<ModelBillingRate>()
                    .eq(ModelBillingRate::getModelConfigId, modelId));
            if (rate != null) {
                return rate;
            }
        }
        ModelBillingRate defaults = new ModelBillingRate();
        defaults.setBillingMode("TOKEN");
        defaults.setInputCreditsPerMillion(1_000_000L);
        defaults.setOutputCreditsPerMillion(1_000_000L);
        defaults.setCreditsPerRound(0L);
        return defaults;
    }

    private long tokenCharge(long inputTokens, long outputTokens, ModelBillingRate rate) {
        long inputRate = rate.getInputCreditsPerMillion() == null ? 1_000_000L : rate.getInputCreditsPerMillion();
        long outputRate = rate.getOutputCreditsPerMillion() == null ? 1_000_000L : rate.getOutputCreditsPerMillion();
        BigInteger numerator = BigInteger.valueOf(Math.max(0L, inputTokens))
                .multiply(BigInteger.valueOf(inputRate))
                .add(BigInteger.valueOf(Math.max(0L, outputTokens))
                        .multiply(BigInteger.valueOf(outputRate)));
        BigInteger charged = numerator.add(BigInteger.valueOf(999_999L))
                .divide(BigInteger.valueOf(1_000_000L));
        try {
            return charged.longValueExact();
        } catch (ArithmeticException ex) {
            throw BusinessException.badRequest("模型计费额度超出支持范围");
        }
    }

    @Override
    @Transactional
    public boolean processPaymentWebhook(String provider, String eventId, String transactionId,
                                         String orderNo, long amountMinor, String currency,
                                         String payloadHash, String rawPayload) {
        if ("WALLET".equalsIgnoreCase(provider))
            throw BusinessException.badRequest("内部钱包订单不支持支付回调");
        PaymentWebhookEvent event = webhookEventMapper.selectOne(new LambdaQueryWrapper<PaymentWebhookEvent>()
                .eq(PaymentWebhookEvent::getProvider, provider)
                .eq(PaymentWebhookEvent::getProviderEventId, eventId));
        if (event != null) return true;
        BillingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BillingOrder>()
                .eq(BillingOrder::getOrderNo, orderNo));
        if (order == null) throw BusinessException.notFound("支付订单不存在");
        if (order.getPaymentProvider() == null || !order.getPaymentProvider().equalsIgnoreCase(provider))
            throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH);
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
        fulfillOrder(order);
        return true;
    }

    private void payOrderFromWallet(BillingOrder order) {
        BillingSetting setting = settingMapper.selectForUpdate(1L);
        if (setting == null) throw new IllegalStateException("计费设置锁定失败");
        long amountUnits = walletUnitsFromMinor(order.getAmountMinor(), order.getCurrency(), setting);
        if (amountUnits > 0) {
            applyWalletDelta(order.getUserId(), -amountUnits, "PURCHASE_DEBIT", "ORDER", order.getId(),
                    "order:" + order.getOrderNo(), "购买 " + order.getOrderNo());
        }
        order.setPaymentProvider("WALLET");
        order.setStatus("PAID");
        order.setPaidTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        fulfillOrder(order);
    }

    private void fulfillOrder(BillingOrder order) {
        Map<String, Object> snapshot = JSON.parseObject(order.getProductSnapshot());
        if ("SUBSCRIPTION".equalsIgnoreCase(order.getProductType())) {
            activateSubscription(order.getUserId(), order.getProductId(), snapshot,
                    "subscription:" + order.getOrderNo(), "BILLING_SUBSCRIPTION_ACTIVATED",
                    "订阅已开通", order.getOrderNo());
            return;
        }
        if ("TOKEN_PACK".equalsIgnoreCase(order.getProductType())) {
            grantTokenPack(order.getUserId(), order.getProductId(), snapshot,
                    "pack:" + order.getOrderNo(), "TOKEN_PACK_PURCHASE",
                    "BILLING_TOKEN_PACK_PURCHASED", order.getOrderNo());
            return;
        }
        throw BusinessException.badRequest("不支持的订单商品类型");
    }

    private void activateSubscription(Long userId, Long fallbackPlanId, Map<String, Object> snapshot,
                                      String outboxKey, String notificationType, String notificationTitle,
                                      String reference) {
        Map<String, Object> effectiveSnapshot = new LinkedHashMap<>(snapshot);
        BillingPlan fallbackPlan = fallbackPlanId == null ? null : planMapper.selectById(fallbackPlanId);
        if (!effectiveSnapshot.containsKey("name") && fallbackPlan != null)
            effectiveSnapshot.put("name", fallbackPlan.getName());
        if (!effectiveSnapshot.containsKey("durationDays") && fallbackPlan != null)
            effectiveSnapshot.put("durationDays", fallbackPlan.getDurationDays());
        if (!effectiveSnapshot.containsKey("quotaTokens") && fallbackPlan != null)
            effectiveSnapshot.put("quotaTokens", fallbackPlan.getQuotaTokens());
        if (!effectiveSnapshot.containsKey("quotaCycleDays") && fallbackPlan != null)
            effectiveSnapshot.put("quotaCycleDays", fallbackPlan.getQuotaCycleDays());
        if (!effectiveSnapshot.containsKey("version") && fallbackPlan != null)
            effectiveSnapshot.put("version", fallbackPlan.getVersion());
        if (!effectiveSnapshot.containsKey("modelIds") && fallbackPlan != null)
            effectiveSnapshot.put("modelIds", planModelIds(fallbackPlan.getId()));

        Long planId = valueLong(effectiveSnapshot.get("planId"), fallbackPlanId);
        long durationDays = valueLong(effectiveSnapshot.get("durationDays"), 0L);
        long quotaTokens = valueLong(effectiveSnapshot.get("quotaTokens"), 0L);
        long quotaCycleDays = valueLong(effectiveSnapshot.get("quotaCycleDays"), 0L);
        String name = String.valueOf(effectiveSnapshot.getOrDefault("name", "订阅方案"));
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(durationDays);
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStatus("ACTIVE");
        subscription.setStartTime(start);
        subscription.setEndTime(end);
        subscription.setNextQuotaTime(quotaCycleDays > 0 ? start.plusDays(quotaCycleDays) : null);
        subscription.setProductSnapshot(JSON.toJSONString(effectiveSnapshot));
        subscription.setCreateTime(start);
        subscription.setUpdateTime(start);
        subscriptionMapper.insert(subscription);
        if (quotaTokens > 0) {
            grantLot(userId, "SUBSCRIPTION", planId, quotaTokens, subscription.getNextQuotaTime(),
                    "SUBSCRIPTION_GRANT");
        }
        addOutbox(outboxKey, userId, notificationType, notificationTitle,
                "方案「" + name + "」已开通。", Map.of("reference", reference,
                        "planId", String.valueOf(planId), "endTime", end.toString()));
    }

    private void grantTokenPack(Long userId, Long fallbackPackId, Map<String, Object> snapshot,
                                String outboxKey, String eventType, String notificationType, String reference) {
        Long packId = valueLong(snapshot.get("tokenPackId"), fallbackPackId);
        long tokens = valueLong(snapshot.get("tokens"), 0L);
        long expireDays = valueLong(snapshot.get("expireDays"), 0L);
        LocalDateTime expire = expireDays > 0 ? LocalDateTime.now().plusDays(expireDays) : null;
        grantLot(userId, "TOKEN_PACK", packId, tokens, expire, eventType);
        addOutbox(outboxKey, userId, notificationType, "Token 补充包购买成功",
                "已增加 " + tokens + " Token。", Map.of("reference", reference, "tokens", tokens));
    }

    private Map<String, Object> walletUnitConfig(BillingSetting setting) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", setting.getWalletCurrency() == null ? "CNY" : setting.getWalletCurrency());
        result.put("fractionalDigits", setting.getWalletFractionalDigits() == null
                ? 2 : setting.getWalletFractionalDigits());
        result.put("unitLabel", setting.getWalletUnitLabel() == null ? "分" : setting.getWalletUnitLabel());
        return result;
    }

    private long walletUnitsFromMinor(Long amountMinor, String currency, BillingSetting setting) {
        if (amountMinor == null || amountMinor < 0) throw BusinessException.badRequest("商品金额不能小于 0");
        String walletCurrency = setting.getWalletCurrency() == null ? "CNY" : setting.getWalletCurrency();
        if (currency == null || !walletCurrency.equalsIgnoreCase(currency)) {
            throw BusinessException.badRequest("商品币种与钱包币种不同，不能使用钱包支付");
        }
        int currencyDigits;
        try {
            currencyDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("商品币种必须为有效的 ISO 4217 代码");
        }
        int walletDigits = setting.getWalletFractionalDigits() == null ? 2 : setting.getWalletFractionalDigits();
        if (currencyDigits < 0) throw BusinessException.badRequest("商品币种不支持金额换算");
        BigInteger amount = BigInteger.valueOf(amountMinor);
        int difference = walletDigits - currencyDigits;
        if (difference > 0) {
            amount = amount.multiply(BigInteger.TEN.pow(difference));
        } else if (difference < 0) {
            BigInteger divisor = BigInteger.TEN.pow(-difference);
            BigInteger[] quotient = amount.divideAndRemainder(divisor);
            if (quotient[1].signum() != 0) {
                throw BusinessException.badRequest("商品金额不能无损换算为钱包单位");
            }
            amount = quotient[0];
        }
        try {
            return amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw BusinessException.badRequest("商品金额超出钱包支持范围");
        }
    }

    private WalletLedger applyWalletDelta(Long userId, long deltaUnits, String eventType,
                                          String sourceType, Long sourceId, String requestId, String reason) {
        WalletLedger existing = walletLedgerMapper.selectOne(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getRequestId, requestId));
        if (existing != null) return existing;
        BillingSetting setting = settingMapper.selectForUpdate(1L);
        if (setting == null) throw new IllegalStateException("计费设置锁定失败");
        LocalDateTime now = LocalDateTime.now();
        walletAccountMapper.insertIfAbsent(userId, now);
        WalletAccount account = walletAccountMapper.selectForUpdate(userId);
        if (account == null) throw new IllegalStateException("钱包账户锁定失败");
        existing = walletLedgerMapper.selectOne(new LambdaQueryWrapper<WalletLedger>()
                .eq(WalletLedger::getRequestId, requestId));
        if (existing != null) return existing;
        long balance = account.getBalanceUnits() == null ? 0L : account.getBalanceUnits();
        BigInteger next = BigInteger.valueOf(balance).add(BigInteger.valueOf(deltaUnits));
        if (next.signum() < 0) throw new BusinessException(ResultCode.WALLET_BALANCE_INSUFFICIENT);
        long newBalance;
        try {
            newBalance = next.longValueExact();
        } catch (ArithmeticException ex) {
            throw BusinessException.badRequest("钱包余额超出支持范围");
        }
        account.setBalanceUnits(newBalance);
        account.setUpdateTime(now);
        walletAccountMapper.updateById(account);

        WalletLedger ledger = new WalletLedger();
        ledger.setUserId(userId);
        ledger.setEventType(eventType);
        ledger.setDeltaUnits(deltaUnits);
        ledger.setBalanceAfterUnits(newBalance);
        ledger.setCurrency(setting.getWalletCurrency());
        ledger.setFractionalDigits(setting.getWalletFractionalDigits());
        ledger.setUnitLabel(setting.getWalletUnitLabel());
        ledger.setSourceType(sourceType);
        ledger.setSourceId(sourceId);
        ledger.setRequestId(requestId);
        ledger.setReason(reason);
        ledger.setCreatedAt(now);
        walletLedgerMapper.insert(ledger);
        return ledger;
    }

    private Map<String, Object> walletAdjustmentResponse(WalletLedger ledger) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deltaUnits", ledger.getDeltaUnits());
        result.put("balanceAfterUnits", ledger.getBalanceAfterUnits());
        result.put("ledgerId", String.valueOf(ledger.getId()));
        result.put("reason", ledger.getReason());
        return result;
    }

    private Map<String, Object> redemptionResponse(BillingRedemption redemption) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(redemption.getId()));
        result.put("benefitType", redemption.getBenefitType());
        result.put("benefit", JSON.parseObject(redemption.getBenefitSnapshot()));
        result.put("redeemedAt", redemption.getRedeemedAt());
        return result;
    }

    private String normalizeRedeemCode(String value) {
        String normalized = value == null ? "" : value.replace("-", "").replace(" ", "")
                .trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-F]{32}")) {
            throw BusinessException.badRequest("兑换码无效或已过期");
        }
        return normalized;
    }

    private String generateRedeemCode() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        String hex = HexFormat.of().withUpperCase().formatHex(bytes);
        return hex.substring(0, 8) + "-" + hex.substring(8, 16) + "-"
                + hex.substring(16, 24) + "-" + hex.substring(24, 32);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String walletRequestId(String operation, Long userId, String idempotencyKey) {
        return operation + ":" + sha256(userId + ":" + idempotencyKey);
    }

    private void checkRedeemRateLimit(Long userId, String clientIp) {
        incrementRateLimit("billing:redeem:rate:user:" + userId, REDEEM_USER_HOURLY_LIMIT);
        if (StringUtils.hasText(clientIp)) {
            incrementRateLimit("billing:redeem:rate:ip:" + sha256(clientIp), REDEEM_IP_HOURLY_LIMIT);
        }
    }

    private void incrementRateLimit(String key, int limit) {
        Long count = redis.opsForValue().increment(key);
        if (count == null) throw new IllegalStateException("兑换请求频率限制计数失败");
        if (count == 1) {
            Boolean expirySet = redis.expire(key, Duration.ofHours(1));
            if (!Boolean.TRUE.equals(expirySet)) throw new IllegalStateException("兑换请求频率限制过期时间设置失败");
        }
        if (count > limit) throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
    }

    private Long parseProductId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BILLING_PRODUCT_NOT_FOUND);
        }
    }

    private Long valueLong(Object value, Long fallback) {
        if (value == null) return fallback;
        try {
            return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("计费商品快照格式不正确");
        }
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
