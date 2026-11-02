package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.recycle.RecycleBinResponse;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RecycleBin;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.RecycleBinMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.service.FileDeletionService;
import bao.gabriel.cognia.system.service.RecycleBinService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RecycleBinMapper recycleBinMapper;
    private final KbDocumentMapper documentMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final FileDeletionService fileDeletionService;
    private final SiteSettingService siteSettingService;

    @Override
    public List<RecycleBinResponse> list(String ownerType, Long ownerId, Long viewerId) {
        String type = normalizeType(ownerType);
        assertAccess(type, ownerId, viewerId);
        return recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>()
                        .eq(RecycleBin::getOwnerType, type)
                        .eq(RecycleBin::getOwnerId, ownerId)
                        .orderByDesc(RecycleBin::getDeleteTime))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void purge(Long id, Long viewerId) {
        RecycleBin entry = recycleBinMapper.selectById(id);
        if (entry == null) {
            throw BusinessException.notFound("回收站记录不存在");
        }
        assertAccess(entry.getOwnerType(), entry.getOwnerId(), viewerId);
        KbDocument document = entry.getDocumentId() == null ? null : documentMapper.selectById(entry.getDocumentId());
        if (document != null) {
            fileDeletionService.permanentDelete(document);
            return;
        }
        if (entry.getObjectKey() != null) {
            fileDeletionService.deleteObjectQuietly(entry.getObjectKey());
        }
        if (entry.getOriginalObjectKey() != null) {
            fileDeletionService.deleteObjectQuietly(entry.getOriginalObjectKey());
        }
        recycleBinMapper.deleteById(id);
    }

    private void assertAccess(String ownerType, Long ownerId, Long viewerId) {
        if (viewerId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        if ("USER".equals(ownerType)) {
            if (!viewerId.equals(ownerId)) {
                throw BusinessException.forbidden("无权访问该回收站");
            }
            return;
        }
        Long membership = teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, ownerId)
                .eq(TeamMember::getUserId, viewerId));
        if (membership == null || membership == 0) {
            throw BusinessException.forbidden("无权访问该团队回收站");
        }
    }

    private RecycleBinResponse toResponse(RecycleBin entry) {
        int retention = entry.getRetentionDays() == null
                ? siteSettingService.getSetting().recycleRetentionDays()
                : entry.getRetentionDays();
        LocalDateTime expiresAt = entry.getDeleteTime() == null ? null : entry.getDeleteTime().plusDays(retention);
        return new RecycleBinResponse(
                entry.getId(), entry.getDocumentId(), entry.getOwnerType(), entry.getOwnerId(),
                entry.getChannelId(), entry.getName(), entry.getSize(), entry.getContentType(),
                entry.getDeletedBy(), format(entry.getDeleteTime()), retention, format(expiresAt));
    }

    private String normalizeType(String ownerType) {
        String type = ownerType == null ? "" : ownerType.trim().toUpperCase();
        if (!"USER".equals(type) && !"TEAM".equals(type)) {
            throw BusinessException.badRequest("归属类型不正确");
        }
        return type;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : FORMATTER.format(value);
    }
}
