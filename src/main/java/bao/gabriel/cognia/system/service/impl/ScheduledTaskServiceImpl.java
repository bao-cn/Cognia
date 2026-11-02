package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.task.ScheduledTaskResponse;
import bao.gabriel.cognia.system.dto.task.TaskRunResponse;
import bao.gabriel.cognia.system.dto.task.UpdateScheduledTaskRequest;
import bao.gabriel.cognia.system.entity.ScheduledTask;
import bao.gabriel.cognia.system.mapper.ScheduledTaskMapper;
import bao.gabriel.cognia.system.service.AuditService;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.system.service.ScheduledTaskService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.TaskHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ScheduledTaskServiceImpl implements ScheduledTaskService {

    private final ScheduledTaskMapper taskMapper;
    private final Map<String, TaskHandler> handlers;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SiteSettingService siteSettingService;

    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cognia-scheduled-task");
        thread.setDaemon(true);
        return thread;
    });

    public ScheduledTaskServiceImpl(ScheduledTaskMapper taskMapper,
                                    List<TaskHandler> handlers,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    SiteSettingService siteSettingService) {
        this.taskMapper = taskMapper;
        this.handlers = handlers.stream()
                .collect(java.util.stream.Collectors.toMap(TaskHandler::code, handler -> handler));
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.siteSettingService = siteSettingService;
    }

    @Override
    public List<ScheduledTaskResponse> list() {
        return taskMapper.selectList(new LambdaQueryWrapper<ScheduledTask>()
                        .orderByAsc(ScheduledTask::getId))
                .stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(ScheduledTaskResponse::id))
                .toList();
    }

    @Override
    public ScheduledTaskResponse update(String code, UpdateScheduledTaskRequest request, Long operatorId) {
        ScheduledTask task = requireTask(code);
        if (request.enabled() != null) {
            task.setEnabled(request.enabled());
        }
        if (request.cronExpression() != null) {
            String cron = request.cronExpression().trim();
            if (cron.isEmpty() || !CronExpression.isValidExpression(cron)) {
                throw BusinessException.badRequest("Cron 表达式格式不正确");
            }
            task.setCronExpression(cron);
        }
        if (request.thresholdMinutes() != null) {
            task.setThresholdMinutes(request.thresholdMinutes());
        }
        if (request.retentionDays() != null) {
            task.setRetentionDays(request.retentionDays());
        }
        if (request.olderThanHours() != null) {
            task.setOlderThanHours(request.olderThanHours());
        }
        if (request.batchSize() != null) {
            task.setBatchSize(request.batchSize());
        }
        validate(task);
        task.setUpdatedBy(operatorId);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return toResponse(task);
    }

    @Override
    public TaskRunResponse runNow(String code, Long operatorId) {
        ScheduledTask task = requireTask(code);
        if (running.putIfAbsent(code, Boolean.TRUE) != null) {
            throw BusinessException.badRequest("任务正在执行，请稍后再试");
        }
        try {
            return execute(task);
        } finally {
            running.remove(code);
        }
    }

    @Override
    public void dispatchDue() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getEnabled, true));
        for (ScheduledTask task : tasks) {
            if (!isDue(task, now)) {
                continue;
            }
            if (running.putIfAbsent(task.getCode(), Boolean.TRUE) != null) {
                continue;
            }
            executor.submit(() -> {
                try {
                    execute(task);
                } catch (Exception ex) {
                    log.error("Scheduled task {} failed unexpectedly", task.getCode(), ex);
                } finally {
                    running.remove(task.getCode());
                }
            });
        }
    }

    private TaskRunResponse execute(ScheduledTask task) {
        long started = System.currentTimeMillis();
        LocalDateTime ranAt = LocalDateTime.now();
        TaskHandler handler = handlers.get(task.getCode());
        boolean success;
        String message;
        if (handler == null) {
            success = false;
            message = "未找到任务处理器：" + task.getCode();
        } else {
            try {
                TaskHandler.TaskResult result = handler.execute(task);
                success = result.success();
                message = result.message();
            } catch (Exception ex) {
                log.error("Scheduled task {} failed", task.getCode(), ex);
                success = false;
                message = ex.getMessage() == null || ex.getMessage().isBlank()
                        ? ex.getClass().getSimpleName() : ex.getMessage();
            }
        }
        int durationMs = (int) Math.max(0, System.currentTimeMillis() - started);
        String status = success ? "SUCCESS" : "FAILED";
        task.setLastRunAt(ranAt);
        task.setLastStatus(status);
        task.setLastMessage(truncate(message, 2000));
        task.setLastDurationMs(durationMs);
        taskMapper.updateById(task);

        try {
            auditService.recordSystem("计划任务", "RUN",
                    "执行计划任务：" + task.getName(), status,
                    success ? null : truncate(message, 500), durationMs);
        } catch (Exception ex) {
            log.warn("Unable to write audit log for scheduled task {}: {}", task.getCode(), ex.getMessage());
        }
        if (!success) {
            try {
                notificationService.notifyAdmins("TASK_FAILED", "计划任务执行失败",
                        task.getName() + "：" + truncate(message, 300),
                        Map.of("taskCode", task.getCode()));
            } catch (Exception ex) {
                log.warn("Unable to notify admins about task {} failure: {}", task.getCode(), ex.getMessage());
            }
        }
        return new TaskRunResponse(task.getCode(), status, message, durationMs,
                siteSettingService.formatDateTime(ranAt));
    }

    private boolean isDue(ScheduledTask task, LocalDateTime now) {
        if (!StringUtils.hasText(task.getCronExpression())) {
            return false;
        }
        CronExpression cron;
        try {
            cron = CronExpression.parse(task.getCronExpression());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid cron expression for task {}: {}", task.getCode(), task.getCronExpression());
            return false;
        }
        LocalDateTime anchor = task.getLastRunAt() == null ? now.minusSeconds(1) : task.getLastRunAt();
        LocalDateTime next = cron.next(anchor);
        return next != null && !next.isAfter(now);
    }

    private void validate(ScheduledTask task) {
        if (!CronExpression.isValidExpression(task.getCronExpression())) {
            throw BusinessException.badRequest("Cron 表达式格式不正确");
        }
        if (task.getThresholdMinutes() != null
                && (task.getThresholdMinutes() < 1 || task.getThresholdMinutes() > 10080)) {
            throw BusinessException.badRequest("超时阈值需在 1 到 10080 分钟之间");
        }
        if (task.getRetentionDays() != null
                && (task.getRetentionDays() < 1 || task.getRetentionDays() > 3650)) {
            throw BusinessException.badRequest("保留天数需在 1 到 3650 天之间");
        }
        if (task.getOlderThanHours() != null
                && (task.getOlderThanHours() < 1 || task.getOlderThanHours() > 8760)) {
            throw BusinessException.badRequest("小时阈值需在 1 到 8760 小时之间");
        }
        int batchSize = task.getBatchSize() == null ? 100 : task.getBatchSize();
        if (batchSize < 1 || batchSize > 1000) {
            throw BusinessException.badRequest("批处理数量需在 1 到 1000 之间");
        }
    }

    private ScheduledTask requireTask(String code) {
        ScheduledTask task = taskMapper.selectOne(new LambdaQueryWrapper<ScheduledTask>()
                .eq(ScheduledTask::getCode, code));
        if (task == null) {
            throw BusinessException.notFound("计划任务不存在");
        }
        return task;
    }

    private ScheduledTaskResponse toResponse(ScheduledTask task) {
        return new ScheduledTaskResponse(
                task.getId(),
                task.getCode(),
                task.getName(),
                task.getDescription(),
                Boolean.TRUE.equals(task.getEnabled()),
                task.getCronExpression(),
                task.getThresholdMinutes(),
                task.getRetentionDays(),
                task.getOlderThanHours(),
                task.getBatchSize(),
                task.getLastRunAt() == null ? null : siteSettingService.formatDateTime(task.getLastRunAt()),
                task.getLastStatus(),
                task.getLastMessage(),
                task.getLastDurationMs()
        );
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
