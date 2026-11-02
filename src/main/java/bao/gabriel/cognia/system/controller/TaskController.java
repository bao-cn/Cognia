package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.task.ScheduledTaskResponse;
import bao.gabriel.cognia.system.dto.task.TaskRunResponse;
import bao.gabriel.cognia.system.dto.task.UpdateScheduledTaskRequest;
import bao.gabriel.cognia.system.service.ScheduledTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin console for the preset scheduled-task catalog. Creating new task types
 * is intentionally unsupported; only enablement, cron and thresholds are
 * editable.
 */
@RestController
@RequestMapping("/api/v1/scheduled-tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ScheduledTaskService scheduledTaskService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('task:read', 'settings:read')")
    public ApiResponse<List<ScheduledTaskResponse>> list() {
        return ApiResponse.success(scheduledTaskService.list());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('task:update', 'settings:update')")
    public ApiResponse<ScheduledTaskResponse> update(@PathVariable String code,
                                                     @Valid @RequestBody UpdateScheduledTaskRequest request) {
        return ApiResponse.success(scheduledTaskService.update(code, request, SecurityUtils.getUserId()));
    }

    @PostMapping("/{code}/run")
    @PreAuthorize("hasAnyAuthority('task:update', 'settings:update')")
    public ApiResponse<TaskRunResponse> runNow(@PathVariable String code) {
        return ApiResponse.success(scheduledTaskService.runNow(code, SecurityUtils.getUserId()));
    }
}
