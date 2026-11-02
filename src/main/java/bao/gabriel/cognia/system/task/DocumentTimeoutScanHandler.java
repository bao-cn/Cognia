package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.ScheduledTask;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.service.DocumentStateMachine;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.system.service.TaskHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marks documents stuck in PENDING/PROCESSING past the configured threshold as
 * FAILED and notifies their owners so they can retry manually.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTimeoutScanHandler implements TaskHandler {

    public static final String CODE = "DOCUMENT_TIMEOUT_SCAN";

    private final KbDocumentMapper documentMapper;
    private final DocumentStateMachine stateMachine;
    private final NotificationService notificationService;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public TaskResult execute(ScheduledTask task) {
        int thresholdMinutes = task.getThresholdMinutes() == null ? 30 : task.getThresholdMinutes();
        int batchSize = task.getBatchSize() == null ? 200 : task.getBatchSize();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(thresholdMinutes);

        List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .in(KbDocument::getStatus,
                        DocumentStateMachine.DocumentStatus.PENDING.name(),
                        DocumentStateMachine.DocumentStatus.PROCESSING.name())
                .apply("COALESCE(update_time, create_time) < {0}", cutoff)
                .orderByAsc(KbDocument::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(batchSize, 1000))));

        int marked = 0;
        for (KbDocument document : documents) {
            try {
                stateMachine.transition(document, DocumentStateMachine.DocumentStatus.FAILED,
                        "处理超时（超过 " + thresholdMinutes + " 分钟），请手动重试");
                documentMapper.updateById(document);
                notifyOwner(document);
                marked++;
            } catch (Exception ex) {
                log.warn("Unable to mark document {} as timed out: {}", document.getId(), ex.getMessage());
            }
        }
        return TaskResult.success("扫描到 " + documents.size() + " 个超时文档，标记失败 " + marked + " 个");
    }

    private void notifyOwner(KbDocument document) {
        String name = document.getName() == null || document.getName().isBlank()
                ? "未命名文档" : document.getName();
        String content = "文档「" + name + "」处理超时，已标记为失败，请手动重试。";
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", document.getId());
        payload.put("channelId", document.getChannelId());
        payload.put("ownerType", document.getOwnerType());
        payload.put("ownerId", document.getOwnerId());
        payload.put("status", DocumentStateMachine.DocumentStatus.FAILED.name());

        if ("USER".equalsIgnoreCase(document.getOwnerType()) && document.getOwnerId() != null) {
            notificationService.notify(document.getOwnerId(), "DOCUMENT_TIMEOUT",
                    "文档处理超时", content, payload);
            return;
        }
        if ("TEAM".equalsIgnoreCase(document.getOwnerType()) && document.getOwnerId() != null) {
            List<TeamMember> members = teamMemberMapper.selectList(
                    new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, document.getOwnerId()));
            for (TeamMember member : members) {
                notificationService.notify(member.getUserId(), "DOCUMENT_TIMEOUT",
                        "文档处理超时", content, payload);
            }
        }
    }
}
