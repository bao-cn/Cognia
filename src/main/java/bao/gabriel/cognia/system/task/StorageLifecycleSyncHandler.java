package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.ScheduledTask;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.TaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies the bucket's native S3 lifecycle rule for the tmp/ logical directory,
 * so temporary multimodal uploads expire without a database sweep.
 */
@Component
@RequiredArgsConstructor
public class StorageLifecycleSyncHandler implements TaskHandler {

    public static final String CODE = "STORAGE_LIFECYCLE_SYNC";

    private final ObjectStorageService objectStorageService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public TaskResult execute(ScheduledTask task) {
        int retentionDays = task.getRetentionDays() == null || task.getRetentionDays() < 1
                ? objectStorageService.getAdminSettings().tempRetentionDays()
                : task.getRetentionDays();
        if (retentionDays < 1) {
            retentionDays = 7;
        }
        String message = objectStorageService.syncTempLifecycle(retentionDays);
        return TaskResult.success(message);
    }
}
