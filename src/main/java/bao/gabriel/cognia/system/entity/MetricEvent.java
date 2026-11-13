package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_metric_event")
public class MetricEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private Long userId;
    private Long channelId;
    private Long conversationId;
    private Long modelId;
    private String modelName;
    private String status;
    private String errorType;
    private Integer durationMs;
    private Boolean retrievalUsed;
    private Boolean retrievalHit;
    private Boolean rerankUsed;
    private Integer citationCount;
    private Integer llmRounds;
    private Boolean refused;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer embeddingTokens;
    private Integer retrievalDurationMs;
    private Integer rerankDurationMs;
    private Integer promptCacheHitTokens;
    private Integer promptCacheMissTokens;
    private LocalDateTime createTime;
}
