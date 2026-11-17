package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.ingestion.IngestionJob;
import bao.gabriel.cognia.rag.ingestion.IngestionJobMapper;
import bao.gabriel.cognia.rag.ingestion.IngestionQueueService;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.channel.ChannelDetailResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelEmbeddingJobResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelEmbeddingResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelProcessingRecord;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/channels/{channelId}/embedding")
@RequiredArgsConstructor
public class ChannelEmbeddingController {

    private final ChannelService channelService;
    private final ChannelMapper channelMapper;
    private final KbDocumentMapper documentMapper;
    private final IngestionJobMapper jobMapper;
    private final IngestionQueueService queueService;
    private final SiteSettingService siteSettingService;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public ApiResponse<ChannelEmbeddingResponse> status(@PathVariable Long channelId) {
        ChannelDetailResponse detail = requireManager(channelId);
        Channel channel = channelMapper.selectById(channelId);
        List<IngestionJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<IngestionJob>()
                .eq(IngestionJob::getChannelId, channelId)
                .orderByDesc(IngestionJob::getId)
                .last("LIMIT 100"));
        Map<Long, List<IngestionJob>> byDocument = new LinkedHashMap<>();
        for (IngestionJob job : jobs) {
            byDocument.computeIfAbsent(job.getDocumentId(), key -> new ArrayList<>()).add(job);
        }
        Map<Long, long[]> chunks = chunkCounts(channelId);
        List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channelId)
                .ne(KbDocument::getStatus, "DELETED")
                .orderByDesc(KbDocument::getCreateTime)
                .orderByDesc(KbDocument::getId));
        List<ChannelProcessingRecord> records = new ArrayList<>();
        long totalChunks = 0;
        long embeddedChunks = 0;
        int completed = 0;
        for (KbDocument document : documents) {
            long[] counts = chunks.getOrDefault(document.getId(), new long[]{0, 0});
            totalChunks += counts[0];
            embeddedChunks += counts[1];
            boolean chunksComplete = counts[0] == 0 || counts[1] >= counts[0];
            if (("READY".equals(document.getStatus()) && chunksComplete) || "FAILED".equals(document.getStatus())) completed++;
            records.add(record(document, byDocument.getOrDefault(document.getId(), List.of()), counts,
                    channel != null && channel.reembedding()));
        }
        records.sort(Comparator.comparing(ChannelProcessingRecord::uploadedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<ChannelEmbeddingJobResponse> jobResponses = jobs.stream().limit(50).map(this::toResponse).toList();
        return ApiResponse.success(new ChannelEmbeddingResponse(
                detail.about().embeddingReady(), detail.about().pendingEmbeddingChunks(),
                channel != null && channel.reembedding(), records.size(), completed,
                totalChunks, embeddedChunks, records, jobResponses));
    }

    @PostMapping("/reembed")
    public ApiResponse<Integer> reembed(@PathVariable Long channelId) {
        requireManager(channelId);
        return ApiResponse.success(channelService.reembedAll(channelId, SecurityUtils.getUserId()));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ApiResponse<Void> retry(@PathVariable Long channelId, @PathVariable Long jobId) {
        requireManager(channelId);
        IngestionJob job = jobMapper.selectById(jobId);
        if (job == null || !channelId.equals(job.getChannelId())) {
            throw BusinessException.notFound("处理任务不存在");
        }
        if (!"DEAD".equals(job.getStatus())) {
            throw BusinessException.badRequest("只有失败的任务可以重试");
        }
        queueService.requeueDead(jobId);
        return ApiResponse.success();
    }

    private Map<Long, long[]> chunkCounts(Long channelId) {
        Map<Long, long[]> result = new HashMap<>();
        String model = embeddingService.activeModelLabel();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT dc.document_id, count(*) AS total,
                       count(*) FILTER (WHERE dc.embedding_status = 'READY' AND dc.embedding_model = ?) AS done
                FROM document_chunk dc JOIN kb_document d ON d.id = dc.document_id
                WHERE d.channel_id = ? AND d.status <> 'DELETED'
                GROUP BY dc.document_id
                """, model, channelId)) {
            result.put(((Number) row.get("document_id")).longValue(),
                    new long[]{((Number) row.get("total")).longValue(), ((Number) row.get("done")).longValue()});
        }
        return result;
    }

    private ChannelProcessingRecord record(KbDocument document, List<IngestionJob> jobs, long[] counts,
                                           boolean channelReembedding) {
        IngestionJob latest = jobs.isEmpty() ? null : jobs.get(0);
        String stage = "UPLOAD";
        String status = "QUEUED";
        if ("READY".equals(document.getStatus())) {
            stage = "EMBED";
            status = counts[0] > 0 && counts[1] < counts[0] ? "PROCESSING" : "SUCCESS";
        } else if ("FAILED".equals(document.getStatus())) {
            stage = latest == null ? "PARSE" : latest.getStep(); status = "FAILED";
        } else if (latest != null) {
            stage = latest.getStep();
            status = "RUNNING".equals(latest.getStatus()) ? "PROCESSING" : "QUEUED";
        } else if (counts[0] > 0) {
            stage = "EMBED";
            status = counts[1] > 0 ? "PROCESSING" : "QUEUED";
        }
        int attempts = jobs.stream().mapToInt(j -> j.getAttempts() == null ? 0 : j.getAttempts()).max().orElse(0);
        String error = latest == null ? document.getErrorMessage() : latest.getErrorMessage();
        return new ChannelProcessingRecord(document.getId(), document.getName(), document.getDocumentType(),
                document.getSize(), format(document.getCreateTime()), status, stage,
                counts[0], counts[1], attempts, error, format(document.getUpdateTime()),
                document.getUploadBatchId(), channelReembedding);
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? null : siteSettingService.formatDateTime(value);
    }

    private ChannelDetailResponse requireManager(Long channelId) {
        ChannelDetailResponse detail = channelService.detail(channelId, SecurityUtils.getUserId());
        if (!detail.canManage()) {
            throw BusinessException.forbidden("只有频道管理员可以管理向量化");
        }
        return detail;
    }

    private ChannelEmbeddingJobResponse toResponse(IngestionJob job) {
        return new ChannelEmbeddingJobResponse(job.getId(), job.getDocumentId(), job.getStep(), job.getStatus(),
                job.getAttempts(), job.getMaxAttempts(), job.getErrorMessage(),
                format(job.getCreatedAt()), format(job.getFinishedAt()));
    }
}
