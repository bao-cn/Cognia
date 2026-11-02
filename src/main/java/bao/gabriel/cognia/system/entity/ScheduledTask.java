package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Preset scheduled task configuration. Rows are seeded by schema.sql; the
 * console may tune parameters but never creates or deletes task types.
 */
@Data
@NoArgsConstructor
@TableName("sys_scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
    private Boolean enabled;
    private String cronExpression;
    private Integer thresholdMinutes;
    private Integer retentionDays;
    private Integer olderThanHours;
    private Integer batchSize;
    private LocalDateTime lastRunAt;
    private String lastStatus;
    private String lastMessage;
    private Integer lastDurationMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long updatedBy;
}
