package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.task.ScheduledTaskResponse;
import bao.gabriel.cognia.system.dto.task.TaskRunResponse;
import bao.gabriel.cognia.system.dto.task.UpdateScheduledTaskRequest;

import java.util.List;

/**
 * Preset scheduled task catalog and dispatcher. Admins can tune parameters
 * and trigger a run, but cannot create new task types.
 */
public interface ScheduledTaskService {

    List<ScheduledTaskResponse> list();

    ScheduledTaskResponse update(String code, UpdateScheduledTaskRequest request, Long operatorId);

    TaskRunResponse runNow(String code, Long operatorId);

    void dispatchDue();
}
