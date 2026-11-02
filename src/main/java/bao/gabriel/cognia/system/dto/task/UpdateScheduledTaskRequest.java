package bao.gabriel.cognia.system.dto.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial parameter update. Omitted fields keep their current value.
 */
public record UpdateScheduledTaskRequest(
        Boolean enabled,

        @Size(max = 64, message = "Cron 表达式长度不能超过 64 个字符")
        String cronExpression,

        @Min(value = 1, message = "超时阈值最小为 1 分钟")
        @Max(value = 10080, message = "超时阈值最大为 10080 分钟")
        Integer thresholdMinutes,

        @Min(value = 1, message = "保留天数最小为 1 天")
        @Max(value = 3650, message = "保留天数最大为 3650 天")
        Integer retentionDays,

        @Min(value = 1, message = "小时阈值最小为 1 小时")
        @Max(value = 8760, message = "小时阈值最大为 8760 小时")
        Integer olderThanHours,

        @Min(value = 1, message = "批处理数量最小为 1")
        @Max(value = 1000, message = "批处理数量最大为 1000")
        Integer batchSize
) {
}
