package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.RecycleBin;
import bao.gabriel.cognia.system.entity.ScheduledTask;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.RecycleBinMapper;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.TaskHandler;
import bao.gabriel.cognia.system.service.VectorStoreCleanupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Purges recycle-bin entries older than the retention window and removes all
 * four copies: MinIO objects, document row, pgvector embeddings and the
 * recycle-bin row itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleBinCleanupHandler implements TaskHandler {

    public static final String CODE = "RECYCLE_BIN_CLEANUP";

    private final RecycleBinMapper recycleBinMapper;
    private final KbDocumentMapper documentMapper;
    private final VectorStoreCleanupService vectorStoreCleanupService;
    private final ObjectStorageService objectStorageService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public TaskResult execute(ScheduledTask task) {
        int defaultRetentionDays = task.getRetentionDays() == null ? 30 : task.getRetentionDays();
        int batchSize = task.getBatchSize() == null ? 200 : task.getBatchSize();

        List<RecycleBin> rows = recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>()
                .apply("delete_time <= NOW() - (COALESCE(retention_days, {0}) * INTERVAL '1 day')",
                        defaultRetentionDays)
                .orderByAsc(RecycleBin::getDeleteTime)
                .last("LIMIT " + Math.max(1, Math.min(batchSize, 1000))));

        int purged = 0;
        int failed = 0;
        for (RecycleBin row : rows) {
            try {
                deleteIfPresent(row.getObjectKey());
                deleteIfPresent(row.getOriginalObjectKey());
                if (row.getDocumentId() != null) {
                    vectorStoreCleanupService.deleteByDocumentId(row.getDocumentId());
                    documentMapper.deleteById(row.getDocumentId());
                }
                recycleBinMapper.deleteById(row.getId());
                purged++;
            } catch (Exception ex) {
                failed++;
                log.warn("Recycle-bin cleanup failed for entry {}: {}", row.getId(), ex.getMessage());
            }
        }
        String message = "清理回收站 " + purged + " 条（按每条记录的留存时长）";
        if (failed > 0) {
            message += "，" + failed + " 条失败待下次重试";
        }
        return TaskResult.success(message);
    }

    private void deleteIfPresent(String key) {
        if (key != null && !key.isBlank()) {
            objectStorageService.deleteObject(key);
        }
    }
}
