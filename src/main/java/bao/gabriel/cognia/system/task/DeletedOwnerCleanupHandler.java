package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RecycleBin;
import bao.gabriel.cognia.system.entity.ScheduledTask;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.RecycleBinMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.TaskHandler;
import bao.gabriel.cognia.system.service.VectorStoreCleanupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Removes every stored file belonging to soft-deleted users and teams, cascades
 * through kb documents / pgvector / recycle-bin rows, then marks the owner as
 * cleaned so it is not processed again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedOwnerCleanupHandler implements TaskHandler {

    public static final String CODE = "DELETED_OWNER_CLEANUP";

    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final KbDocumentMapper documentMapper;
    private final RecycleBinMapper recycleBinMapper;
    private final VectorStoreCleanupService vectorStoreCleanupService;
    private final ObjectStorageService objectStorageService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public TaskResult execute(ScheduledTask task) {
        if (!objectStorageService.isConfigured()) {
            throw new IllegalStateException("对象存储未配置，无法清理已注销用户/团队文件");
        }
        int batchSize = task.getBatchSize() == null ? 100 : task.getBatchSize();
        int limit = Math.max(1, Math.min(batchSize, 1000));

        int users = 0;
        int teams = 0;
        for (User user : userMapper.selectDeletedPendingFileCleanup(limit)) {
            if (cleanupOwner("USER", user.getId())) {
                userMapper.markFilesCleaned(user.getId(), LocalDateTime.now());
                users++;
            }
        }
        for (Team team : teamMapper.selectDeletedPendingFileCleanup(limit)) {
            if (cleanupOwner("TEAM", team.getId())) {
                teamMapper.markFilesCleaned(team.getId(), LocalDateTime.now());
                teams++;
            }
        }
        return TaskResult.success("清理已注销用户 " + users + " 个、团队 " + teams + " 个");
    }

    private boolean cleanupOwner(String ownerType, Long ownerId) {
        if (ownerId == null) {
            return false;
        }
        try {
            // The logical directory owns all channel documents for this owner.
            objectStorageService.deletePrefix("kb/" + ownerId + "/");
            deleteIfPresent("avatar/" + ownerId + ".webp");
            deleteIfPresent("avatar/" + ownerId + "_thumb.webp");

            List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                    .eq(KbDocument::getOwnerType, ownerType)
                    .eq(KbDocument::getOwnerId, ownerId));
            for (KbDocument document : documents) {
                deleteIfPresent(document.getObjectKey());
                deleteIfPresent(document.getOriginalKey());
                vectorStoreCleanupService.deleteByDocumentId(document.getId());
                documentMapper.deleteById(document.getId());
            }

            List<RecycleBin> recycleRows = recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>()
                    .eq(RecycleBin::getOwnerType, ownerType)
                    .eq(RecycleBin::getOwnerId, ownerId));
            for (RecycleBin row : recycleRows) {
                deleteIfPresent(row.getObjectKey());
                deleteIfPresent(row.getOriginalObjectKey());
                if (row.getDocumentId() != null) {
                    vectorStoreCleanupService.deleteByDocumentId(row.getDocumentId());
                    documentMapper.deleteById(row.getDocumentId());
                }
                recycleBinMapper.deleteById(row.getId());
            }
            return true;
        } catch (Exception ex) {
            log.warn("Deleted-owner cleanup failed for {} {}: {}", ownerType, ownerId, ex.getMessage());
            return false;
        }
    }

    private void deleteIfPresent(String key) {
        if (key != null && !key.isBlank()) {
            objectStorageService.deleteObject(key);
        }
    }
}
