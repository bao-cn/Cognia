package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.rag.ingestion.IngestionJob;
import bao.gabriel.cognia.rag.ingestion.IngestionJobMapper;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingNotifier {

    private final KbDocumentMapper documentMapper;
    private final ChannelMapper channelMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final IngestionJobMapper jobMapper;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final ChannelAlertService channelAlertService;
    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redis;

    public void finished(KbDocument document, boolean success) {
        if (document == null) return;
        Channel channel = channel(document.getChannelId());
        if (channel != null && channel.reembedding()) {
            checkReembedding(channel.getId(), success);
            return;
        }
        if (document.getUploadBatchId() != null && !document.getUploadBatchId().isBlank()) {
            notifyBatch(document, success);
        } else {
            notifySingle(document, success);
        }
    }

    private void notifySingle(KbDocument document, boolean success) {
        if (document.getCreatedBy() == null) return;
        String title = success ? "文件处理完成" : "文件处理失败";
        String content = success
                ? "「" + document.getName() + "」已完成解析和向量化，可以用于检索。"
                : "「" + document.getName() + "」处理失败，请前往频道处理记录查看原因并重试。";
        notificationService.notify(document.getCreatedBy(), success ? "DOCUMENT_READY" : "DOCUMENT_FAILED",
                title, content, payload(document, null, null));
        Channel channel = channel(document.getChannelId());
        if (channel != null) {
            channelAlertService.notifyAdmins(channel.getId(),
                    "【" + channel.getName() + "】" + title,
                    content + "\n频道：" + channel.getName());
        }
    }

    private void notifyBatch(KbDocument document, boolean success) {
        List<KbDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getUploadBatchId, document.getUploadBatchId())
                .eq(KbDocument::getChannelId, document.getChannelId()));
        if (docs.stream().anyMatch(d -> "PENDING".equals(d.getStatus()) || "PROCESSING".equals(d.getStatus()))) {
            return;
        }
        String key = "doc:batch:done:" + document.getUploadBatchId();
        Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(1));
        if (Boolean.FALSE.equals(first)) return;
        long ok = docs.stream().filter(d -> "READY".equals(d.getStatus())).count();
        long failed = docs.stream().filter(d -> "FAILED".equals(d.getStatus())).count();
        if (document.getCreatedBy() != null) {
            String content = failed == 0
                    ? "本次上传的 " + ok + " 个文件已全部处理完成，可以用于检索。"
                    : "本次上传完成：" + ok + " 个成功，" + failed + " 个失败。失败文件可在频道处理记录中重试。";
            notificationService.notify(document.getCreatedBy(), "DOCUMENT_BATCH_DONE",
                    "批量文件处理完成", content, payload(document, ok, failed));
        }
        Channel channel = channel(document.getChannelId());
        if (channel != null) {
            channelAlertService.notifyAdmins(channel.getId(),
                    "【" + channel.getName() + "】批量文件处理完成",
                    "本次上传：" + ok + " 个成功，" + failed + " 个失败。\n频道：" + channel.getName());
        }
    }

    private void checkReembedding(Long channelId, boolean success) {
        if (channelId == null) return;
        Channel channel = channel(channelId);
        if (channel == null || !channel.reembedding()) return;
        if (!success) {
            Long active = jobMapper.selectCount(new LambdaQueryWrapper<IngestionJob>()
                    .eq(IngestionJob::getChannelId, channelId)
                    .eq(IngestionJob::getStep, "EMBED")
                    .in(IngestionJob::getStatus, "QUEUED", "RUNNING"));
            if (active != null && active > 0) return;
            channel.setReembedding(false);
            channelMapper.updateById(channel);
            String failed = "频道「" + channel.getName() + "」重新向量化未全部完成，请前往处理记录重试失败文件。";
            for (Long userId : managerIds(channel)) {
                notificationService.notify(userId, "CHANNEL_REEMBED_FAILED", "频道重新向量化未完成",
                        failed, payload(null, null, null));
            }
            channelAlertService.notifyAdmins(channelId, "【" + channel.getName() + "】重新向量化未完成", failed);
            return;
        }
        Long active = jobMapper.selectCount(new LambdaQueryWrapper<IngestionJob>()
                .eq(IngestionJob::getChannelId, channelId)
                .eq(IngestionJob::getStep, "EMBED")
                .in(IngestionJob::getStatus, "QUEUED", "RUNNING"));
        if (active != null && active > 0) return;
        Long total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk dc JOIN kb_document d ON d.id = dc.document_id
                WHERE d.channel_id = ? AND d.status <> 'DELETED'""", Long.class, channelId);
        Long ready = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk dc JOIN kb_document d ON d.id = dc.document_id
                WHERE d.channel_id = ? AND d.status <> 'DELETED' AND dc.embedding_status = 'READY'
                  AND dc.embedding_model = ?""", Long.class, channelId, embeddingService.activeModelLabel());
        if (total == null || ready == null || ready < total) return;
        channel.setReembedding(false);
        channelMapper.updateById(channel);
        String content = "频道「" + channel.getName() + "」已完成重新向量化，可以正常用于检索。";
        for (Long userId : managerIds(channel)) {
            notificationService.notify(userId, "CHANNEL_REEMBED_COMPLETE", "频道重新向量化完成",
                    content, payload(null, null, null));
        }
        channelAlertService.notifyAdmins(channelId,
                "【" + channel.getName() + "】重新向量化完成", content);
    }

    private Set<Long> managerIds(Channel channel) {
        Set<Long> ids = new LinkedHashSet<>();
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            if (channel.getOwnerId() != null) ids.add(channel.getOwnerId());
            return ids;
        }
        Team team = teamMapper.selectById(channel.getOwnerId());
        if (team == null) return ids;
        if (team.getOwnerId() != null) ids.add(team.getOwnerId());
        List<TeamMember> managers = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId()).in(TeamMember::getRole, "OWNER", "MANAGER"));
        managers.forEach(m -> { if (m.getUserId() != null) ids.add(m.getUserId()); });
        return ids;
    }

    private Channel channel(Long id) {
        return id == null ? null : channelMapper.selectById(id);
    }

    private Map<String, Object> payload(KbDocument document, Long success, Long failed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (document != null) {
            payload.put("documentId", String.valueOf(document.getId()));
            payload.put("channelId", String.valueOf(document.getChannelId()));
            payload.put("name", document.getName());
            if (StringUtils.hasText(document.getUploadBatchId())) {
                payload.put("batchId", document.getUploadBatchId());
            }
        }
        if (success != null) payload.put("success", success);
        if (failed != null) payload.put("failed", failed);
        return payload;
    }
}
