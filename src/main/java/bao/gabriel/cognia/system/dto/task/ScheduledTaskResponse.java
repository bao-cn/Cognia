package bao.gabriel.cognia.system.dto.task;

/**
 * Console view of a preset scheduled task. Task types are seeded by
 * schema.sql; only their parameters are editable.
 */
public record ScheduledTaskResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean enabled,
        String cronExpression,
        Integer thresholdMinutes,
        Integer retentionDays,
        Integer olderThanHours,
        Integer batchSize,
        String lastRunAt,
        String lastStatus,
        String lastMessage,
        Integer lastDurationMs
) {
}
