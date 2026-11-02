package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RecycleBin;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.RecycleBinMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Single entry point for knowledge-base file deletion.
 *
 * <p>Logical deletion keeps the physical object at its original key, records
 * the path / delete time / retention period in {@code kb_recycle_bin}, and
 * marks the document DELETED so it disappears from all read paths. Permanent
 * deletion removes the object, vectors and every metadata row immediately.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionService {

    private final KbDocumentMapper documentMapper;
    private final RecycleBinMapper recycleBinMapper;
    private final ObjectStorageService objectStorageService;
    private final VectorStoreCleanupService vectorCleanupService;
    private final DocumentStateMachine stateMachine;
    private final SiteSettingService siteSettingService;

    @Transactional
    public void logicalDelete(KbDocument document, Long operatorId) {
        if (document == null) {
            throw BusinessException.notFound("文档不存在");
        }
        if (DocumentStateMachine.DocumentStatus.DELETED.name().equals(document.getStatus())) {
            return;
        }
        int retentionDays = siteSettingService.getSetting().recycleRetentionDays();
        RecycleBin entry = new RecycleBin();
        entry.setDocumentId(document.getId());
        entry.setOwnerType(document.getOwnerType());
        entry.setOwnerId(document.getOwnerId());
        entry.setChannelId(document.getChannelId());
        entry.setObjectKey(document.getObjectKey());
        entry.setOriginalObjectKey(document.getOriginalKey());
        entry.setName(document.getName());
        entry.setSize(document.getSize() == null ? 0 : document.getSize());
        entry.setContentType(document.getContentType());
        entry.setDeletedBy(operatorId);
        entry.setDeleteTime(LocalDateTime.now());
        entry.setRetentionDays(retentionDays);
        entry.setCreateTime(LocalDateTime.now());
        recycleBinMapper.insert(entry);

        stateMachine.transition(document, DocumentStateMachine.DocumentStatus.DELETED, null);
        documentMapper.updateById(document);
    }

    @Transactional
    public void permanentDelete(KbDocument document) {
        if (document == null) {
            return;
        }
        deleteObjectQuietly(document.getObjectKey());
        if (document.getOriginalKey() != null && !document.getOriginalKey().equals(document.getObjectKey())) {
            deleteObjectQuietly(document.getOriginalKey());
        }
        vectorCleanupService.deleteByDocumentId(document.getId());
        recycleBinMapper.delete(new LambdaQueryWrapper<RecycleBin>()
                .eq(RecycleBin::getDocumentId, document.getId()));
        documentMapper.deleteById(document.getId());
    }

    public void deleteObjectQuietly(String key) {
        if (key == null || key.isBlank() || !objectStorageService.isConfigured()) {
            return;
        }
        try {
            objectStorageService.deleteObject(key);
        } catch (Exception ex) {
            log.warn("Unable to delete object {}: {}", key, ex.getMessage());
        }
    }
}
