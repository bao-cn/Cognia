package bao.gabriel.cognia.rag.ingestion;

import bao.gabriel.cognia.system.service.AlertRecipientService;
import bao.gabriel.cognia.system.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionAlertMonitor {
    private final IngestionSettingService settingService;
    private final IngestionMetricsService metricsService;
    private final IngestionQueueService queueService;
    private final EmbeddingHealthService healthService;
    private final EmailService emailService;
    private final AlertRecipientService recipientService;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void check() {
        IngestionSetting setting = settingService.get();
        if (!setting.alertEmailEnabled()) {
            return;
        }
        int depth = queueService.depth();
        if (depth >= setting.queueAlertDepth()) {
            alert("queue", "文档处理队列积压",
                    "当前队列深度 " + depth + "，已超过阈值 " + setting.queueAlertDepth() + "。");
        }
        double failureRate = failureRate();
        if (failureRate >= setting.failureAlertPercent()) {
            alert("failure", "文档处理失败率升高",
                    String.format("近 24 小时失败率 %.1f%%，已超过阈值 %d%%。", failureRate, setting.failureAlertPercent()));
        }
        double health = healthService.successRate();
        if (health < 90 || healthService.consecutiveFailures() >= setting.healthFailureThreshold()) {
            alert("health", "Embedding 服务健康度异常",
                    String.format("近 10 分钟成功率 %.1f%%，连续失败 %d 次。", health, healthService.consecutiveFailures()));
        }
    }

    private double failureRate() {
        Object value = metricsService.metrics().get("failureRate");
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private void alert(String key, String subject, String message) {
        Instant last = lastSent.get(key);
        if (last != null && Duration.between(last, Instant.now()).toMinutes() < 30) {
            return;
        }
        try {
            var current = settingService.get();
            var recipients = recipientService.opsEmails(current.alertRoleCode(), current.getAlertRecipient());
            if (recipients.isEmpty()) {
                log.warn("Ingestion alert skipped: no recipient for role {}", current.alertRoleCode());
                return;
            }
            for (String recipient : recipients) {
                emailService.sendAlert(recipient, subject, message);
            }
            lastSent.put(key, Instant.now());
            log.info("Ingestion alert sent: {}", subject);
        } catch (Exception ex) {
            log.warn("Ingestion alert email failed: {}", ex.getMessage());
        }
    }
}
