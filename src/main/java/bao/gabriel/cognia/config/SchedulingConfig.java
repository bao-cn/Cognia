package bao.gabriel.cognia.config;

import bao.gabriel.cognia.system.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Enables the Spring scheduler used by the preset task dispatcher and the SSE
 * heartbeat. The dispatcher runs on a short fixed delay and decides per task
 * whether its cron is due.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig {

    private final ScheduledTaskService scheduledTaskService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void dispatchScheduledTasks() {
        try {
            scheduledTaskService.dispatchDue();
        } catch (Exception ex) {
            log.error("Scheduled task dispatch failed", ex);
        }
    }
}
