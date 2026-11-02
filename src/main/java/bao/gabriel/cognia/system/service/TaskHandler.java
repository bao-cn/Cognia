package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.entity.ScheduledTask;

/**
 * One preset task implementation. Handlers are looked up by task code.
 */
public interface TaskHandler {

    String code();

    TaskResult execute(ScheduledTask task);

    record TaskResult(boolean success, String message) {

        public static TaskResult success(String message) {
            return new TaskResult(true, message);
        }

        public static TaskResult failure(String message) {
            return new TaskResult(false, message);
        }
    }
}
