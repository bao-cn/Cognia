package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.ScheduledTask;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.TaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Aborts multipart uploads under kb/ that were started long ago. S3 lifecycle
 * rules cannot express a separate incomplete-multipart timeout per prefix, so
 * this is handled by the scheduler.
 */
@Component
@RequiredArgsConstructor
public class MultipartCleanupHandler implements TaskHandler {

    public static final String CODE = "MULTIPART_CLEANUP";
    private static final String PREFIX = "kb/";

    private final ObjectStorageService objectStorageService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public TaskResult execute(ScheduledTask task) {
        int olderThanHours = task.getOlderThanHours() == null ? 24 : task.getOlderThanHours();
        int batchSize = task.getBatchSize() == null ? 200 : task.getBatchSize();
        int aborted = objectStorageService.abortIncompleteMultipartUploads(PREFIX, olderThanHours, batchSize);
        return TaskResult.success("已清理 " + aborted + " 个超过 " + olderThanHours + " 小时的残留分片");
    }
}
