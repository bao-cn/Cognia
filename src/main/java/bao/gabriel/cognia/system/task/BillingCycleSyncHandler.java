package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.*;
import bao.gabriel.cognia.system.mapper.*;
import bao.gabriel.cognia.system.service.TaskHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BillingCycleSyncHandler implements TaskHandler {
    public static final String CODE = "BILLING_CYCLE_SYNC";

    private final UserSubscriptionMapper subscriptionMapper;
    private final BillingPlanMapper planMapper;
    private final TokenLotMapper lotMapper;
    private final TokenLedgerMapper ledgerMapper;
    private final BillingNotificationOutboxMapper outboxMapper;

    @Override public String code() { return CODE; }

    @Override
    public TaskResult execute(ScheduledTask task) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = task.getBatchSize() == null ? 500 : Math.min(task.getBatchSize(), 1000);
        int granted = 0;
        int expiredSubscriptions = 0;

        List<UserSubscription> subscriptions = subscriptionMapper.selectList(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getStatus, "ACTIVE")
                .le(UserSubscription::getEndTime, now).orderByAsc(UserSubscription::getId).last("LIMIT " + batchSize));
        for (UserSubscription subscription : subscriptions) {
            subscription.setStatus("EXPIRED");
            subscription.setUpdateTime(now);
            subscriptionMapper.updateById(subscription);
            addOutbox("subscription-expired:" + subscription.getId(), subscription.getUserId(),
                    "BILLING_SUBSCRIPTION_EXPIRED", "订阅已到期", "当前订阅已到期，未使用的订阅周期额度已失效。",
                    java.util.Map.of("subscriptionId", String.valueOf(subscription.getId()),
                            "endTime", subscription.getEndTime().toString()));
            expiredSubscriptions++;
        }

        List<UserSubscription> due = subscriptionMapper.selectList(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getStatus, "ACTIVE")
                .gt(UserSubscription::getEndTime, now)
                .le(UserSubscription::getNextQuotaTime, now)
                .orderByAsc(UserSubscription::getNextQuotaTime).last("LIMIT " + batchSize));
        for (UserSubscription subscription : due) {
            BillingPlan plan = planMapper.selectById(subscription.getPlanId());
            if (plan == null || plan.getQuotaTokens() == null || plan.getQuotaTokens() <= 0) continue;
            int cycleDays = plan.getQuotaCycleDays() == null ? 30 : plan.getQuotaCycleDays();
            LocalDateTime next = subscription.getNextQuotaTime() == null ? now.plusDays(cycleDays)
                    : subscription.getNextQuotaTime().plusDays(cycleDays);
            if (next.isAfter(subscription.getEndTime())) next = subscription.getEndTime();

            TokenLot lot = new TokenLot();
            lot.setUserId(subscription.getUserId());
            lot.setSourceType("SUBSCRIPTION");
            lot.setSourceId(plan.getId());
            lot.setGrantedTokens(plan.getQuotaTokens());
            lot.setRemainingTokens(plan.getQuotaTokens());
            lot.setHeldTokens(0L);
            lot.setExpireTime(next);
            lot.setCreateTime(now);
            lot.setUpdateTime(now);
            lotMapper.insert(lot);

            TokenLedger ledger = new TokenLedger();
            ledger.setUserId(subscription.getUserId());
            ledger.setEventType("SUBSCRIPTION_QUOTA_GRANT");
            ledger.setDeltaTokens(plan.getQuotaTokens());
            ledger.setBalanceAfter(balance(subscription.getUserId()));
            ledger.setSourceType("SUBSCRIPTION");
            ledger.setSourceId(plan.getId());
            ledger.setCreatedAt(now);
            ledgerMapper.insert(ledger);

            subscription.setNextQuotaTime(next);
            subscription.setUpdateTime(now);
            subscriptionMapper.updateById(subscription);
            addOutbox("quota:" + subscription.getId() + ":" + next,
                    subscription.getUserId(), "BILLING_QUOTA_GRANTED",
                    "订阅周期额度已发放", "已发放 " + plan.getQuotaTokens() + " Token。",
                    java.util.Map.of("subscriptionId", String.valueOf(subscription.getId()),
                            "tokens", plan.getQuotaTokens(), "expireTime", next.toString()));
            granted++;
        }

        int expiredLots = expireLots(now, batchSize);
        return TaskResult.success("发放周期额度 " + granted + " 次，订阅到期 " + expiredSubscriptions
                + " 个，过期 Token 分桶 " + expiredLots + " 个");
    }

    private int expireLots(LocalDateTime now, int batchSize) {
        List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                .gt(TokenLot::getRemainingTokens, 0).le(TokenLot::getExpireTime, now)
                .orderByAsc(TokenLot::getExpireTime).last("LIMIT " + batchSize));
        for (TokenLot lot : lots) {
            long amount = lot.getRemainingTokens();
            lot.setRemainingTokens(0L);
            lot.setUpdateTime(now);
            lotMapper.updateById(lot);
            TokenLedger ledger = new TokenLedger();
            ledger.setUserId(lot.getUserId());
            ledger.setEventType("LOT_EXPIRED");
            ledger.setDeltaTokens(-amount);
            ledger.setBalanceAfter(balance(lot.getUserId()));
            ledger.setSourceType(lot.getSourceType());
            ledger.setSourceId(lot.getSourceId());
            ledger.setCreatedAt(now);
            ledgerMapper.insert(ledger);
        }
        return lots.size();
    }

    private long balance(Long userId) {
        List<TokenLot> lots = lotMapper.selectList(new LambdaQueryWrapper<TokenLot>()
                .eq(TokenLot::getUserId, userId)
                .and(w -> w.isNull(TokenLot::getExpireTime).or().gt(TokenLot::getExpireTime, LocalDateTime.now())));
        return lots.stream().mapToLong(l -> Math.max(0L,
                l.getRemainingTokens() - (l.getHeldTokens() == null ? 0L : l.getHeldTokens()))).sum();
    }

    private void addOutbox(String key, Long userId, String type, String title, String content,
                           java.util.Map<String, Object> payload) {
        BillingNotificationOutbox outbox = new BillingNotificationOutbox();
        outbox.setEventKey(key);
        outbox.setUserId(userId);
        outbox.setType(type);
        outbox.setTitle(title);
        outbox.setContent(content);
        outbox.setPayloadJson(com.alibaba.fastjson2.JSON.toJSONString(payload));
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextAttemptTime(LocalDateTime.now());
        outbox.setCreateTime(LocalDateTime.now());
        outboxMapper.insert(outbox);
    }
}
