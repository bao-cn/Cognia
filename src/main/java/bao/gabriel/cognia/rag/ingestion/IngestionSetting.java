package bao.gabriel.cognia.rag.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_ingestion_setting")
public class IngestionSetting {
    public static final long SINGLETON_ID = 1L;
    @TableId(type = IdType.INPUT)
    private Long id;
    private Integer concurrency;
    private Integer queueLimit;
    private Integer maxRetries;
    private Integer retryBackoffSeconds;
    private Integer hnswM;
    private Integer hnswEfConstruction;
    private Integer hnswEfSearch;
    private Integer healthFailureThreshold;
    private Integer queueAlertDepth;
    private Integer failureAlertPercent;
    private Boolean alertEmailEnabled;
    private String alertRecipient;
    private String alertRoleCode;
    private Long updatedBy;
    private LocalDateTime updateTime;

    public int concurrency() { return concurrency == null ? 2 : Math.max(1, Math.min(concurrency, 16)); }
    public int queueLimit() { return queueLimit == null ? 500 : Math.max(10, queueLimit); }
    public int maxRetries() { return maxRetries == null ? 5 : Math.max(0, Math.min(maxRetries, 20)); }
    public int retryBackoffSeconds() { return retryBackoffSeconds == null ? 30 : Math.max(5, retryBackoffSeconds); }
    public int hnswM() { return hnswM == null ? 16 : Math.max(4, Math.min(hnswM, 64)); }
    public int hnswEfConstruction() { return hnswEfConstruction == null ? 200 : Math.max(40, Math.min(hnswEfConstruction, 1000)); }
    public int hnswEfSearch() { return hnswEfSearch == null ? 100 : Math.max(10, Math.min(hnswEfSearch, 1000)); }
    public int healthFailureThreshold() { return healthFailureThreshold == null ? 3 : Math.max(1, healthFailureThreshold); }
    public int queueAlertDepth() { return queueAlertDepth == null ? 100 : Math.max(1, queueAlertDepth); }
    public int failureAlertPercent() { return failureAlertPercent == null ? 10 : Math.max(1, Math.min(failureAlertPercent, 100)); }
    public boolean alertEmailEnabled() { return Boolean.TRUE.equals(alertEmailEnabled); }
    public String alertRoleCode() { return alertRoleCode == null || alertRoleCode.isBlank() ? "OPS" : alertRoleCode; }
}
