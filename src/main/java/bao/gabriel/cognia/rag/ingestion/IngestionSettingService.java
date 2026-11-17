package bao.gabriel.cognia.rag.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IngestionSettingService {

    private final IngestionSettingMapper mapper;
    private volatile IngestionSetting cached;
    private volatile long cachedAt;

    public IngestionSetting get() {
        IngestionSetting value = cached;
        if (value != null && System.currentTimeMillis() - cachedAt < Duration.ofSeconds(10).toMillis()) {
            return value;
        }
        IngestionSetting setting = mapper.selectById(IngestionSetting.SINGLETON_ID);
        if (setting == null) {
            setting = defaults();
            mapper.insert(setting);
        }
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return setting;
    }

    public IngestionSetting update(IngestionSetting input, Long operatorId) {
        IngestionSetting setting = get();
        if (input.getConcurrency() != null) setting.setConcurrency(clamp(input.getConcurrency(), 1, 16));
        if (input.getQueueLimit() != null) setting.setQueueLimit(clamp(input.getQueueLimit(), 10, 100000));
        if (input.getMaxRetries() != null) setting.setMaxRetries(clamp(input.getMaxRetries(), 0, 20));
        if (input.getRetryBackoffSeconds() != null) setting.setRetryBackoffSeconds(clamp(input.getRetryBackoffSeconds(), 5, 3600));
        if (input.getHnswM() != null) setting.setHnswM(clamp(input.getHnswM(), 4, 64));
        if (input.getHnswEfConstruction() != null) setting.setHnswEfConstruction(clamp(input.getHnswEfConstruction(), 40, 1000));
        if (input.getHnswEfSearch() != null) setting.setHnswEfSearch(clamp(input.getHnswEfSearch(), 10, 1000));
        if (input.getHealthFailureThreshold() != null) setting.setHealthFailureThreshold(clamp(input.getHealthFailureThreshold(), 1, 100));
        if (input.getQueueAlertDepth() != null) setting.setQueueAlertDepth(clamp(input.getQueueAlertDepth(), 1, 100000));
        if (input.getFailureAlertPercent() != null) setting.setFailureAlertPercent(clamp(input.getFailureAlertPercent(), 1, 100));
        if (input.getAlertEmailEnabled() != null) setting.setAlertEmailEnabled(input.getAlertEmailEnabled());
        if (input.getAlertRecipient() != null) setting.setAlertRecipient(blank(input.getAlertRecipient()) ? null : input.getAlertRecipient().trim());
        if (input.getAlertRoleCode() != null) setting.setAlertRoleCode(blank(input.getAlertRoleCode()) ? "OPS" : input.getAlertRoleCode().trim());
        setting.setUpdatedBy(operatorId);
        setting.setUpdateTime(LocalDateTime.now());
        mapper.updateById(setting);
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return setting;
    }

    private IngestionSetting defaults() {
        IngestionSetting setting = new IngestionSetting();
        setting.setId(IngestionSetting.SINGLETON_ID);
        setting.setConcurrency(2);
        setting.setQueueLimit(500);
        setting.setMaxRetries(5);
        setting.setRetryBackoffSeconds(30);
        setting.setHnswM(16);
        setting.setHnswEfConstruction(200);
        setting.setHnswEfSearch(100);
        setting.setHealthFailureThreshold(3);
        setting.setQueueAlertDepth(100);
        setting.setFailureAlertPercent(10);
        setting.setAlertEmailEnabled(false);
        setting.setAlertRoleCode("OPS");
        setting.setUpdateTime(LocalDateTime.now());
        return setting;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
