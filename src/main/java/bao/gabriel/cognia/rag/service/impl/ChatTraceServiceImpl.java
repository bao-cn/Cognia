package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.rag.service.ChatTraceService;
import bao.gabriel.cognia.rag.service.trace.TraceRecorder;
import bao.gabriel.cognia.system.dto.trace.TraceDetailResponse;
import bao.gabriel.cognia.system.dto.trace.TraceStatsResponse;
import bao.gabriel.cognia.system.dto.trace.TraceSummaryResponse;
import bao.gabriel.cognia.system.entity.ChatTrace;
import bao.gabriel.cognia.system.mapper.ChatTraceMapper;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTraceServiceImpl implements ChatTraceService {

    private static final int MAX_QUESTION = 2000;

    private final ChatTraceMapper mapper;
    private final SiteSettingService siteSettingService;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public TraceRecorder start(Long userId, String conversationId, ResolvedModel model, String question,
                               boolean debug) {
        if (!debug) {
            return null;
        }
        TraceRecorder recorder = new TraceRecorder(TraceRecorder.newTraceId(), userId, conversationId);
        recorder.put("meta", "modelName", model == null ? null : model.modelId());
        recorder.put("meta", "protocol", model == null ? null : model.protocol());
        recorder.put("meta", "modelType", model == null ? null : model.modelType());
        recorder.put("meta", "modelId", model == null ? null : model.id());
        recorder.put("meta", "question", question);
        return recorder;
    }

    @Override
    public void finish(TraceRecorder recorder, String status, String answer) {
        if (recorder == null) {
            return;
        }
        recorder.finishTiming();
        recorder.stage("done", System.nanoTime(), status);
        try {
            ChatTrace trace = new ChatTrace();
            trace.setTraceId(recorder.traceId());
            trace.setConversationId(truncate(recorder.root().path("conversationId").asText(null), 32));
            JsonNode userIdNode = recorder.root().path("userId");
            trace.setUserId(userIdNode.isNumber() ? userIdNode.asLong() : null);
            JsonNode meta = recorder.root().path("meta");
            trace.setModelId(meta.path("modelId").isNumber() ? meta.path("modelId").asLong() : null);
            trace.setModelName(truncate(meta.path("modelName").asText(null), 255));
            trace.setProtocol(truncate(meta.path("protocol").asText(null), 32));
            trace.setQuestion(truncate(meta.path("question").asText(null), MAX_QUESTION));
            trace.setAnswer(truncate(answer, 8000));
            trace.setStatus(status);
            trace.setDurationMs(recorder.durationMs());
            trace.setTraceJson(recorder.toJson());
            trace.setCreateTime(LocalDateTime.now());
            mapper.insert(trace);
        } catch (Exception ex) {
            log.warn("Failed to persist chat trace: {}", ex.getMessage());
        }
    }

    @Override
    public PageResult<TraceSummaryResponse> page(long page, long size, String status, String keyword) {
        long normalizedPage = Math.max(1, page);
        long normalizedSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<ChatTrace> wrapper = new LambdaQueryWrapper<ChatTrace>()
                .orderByDesc(ChatTrace::getId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(ChatTrace::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String like = keyword.trim();
            wrapper.and(query -> query.like(ChatTrace::getQuestion, like)
                    .or().like(ChatTrace::getTraceId, like)
                    .or().like(ChatTrace::getModelName, like));
        }
        Page<ChatTrace> result = mapper.selectPage(new Page<>(normalizedPage, normalizedSize), wrapper);
        List<TraceSummaryResponse> records = result.getRecords().stream()
                .map(item -> new TraceSummaryResponse(item.getTraceId(), item.getConversationId(), item.getUserId(),
                        item.getModelName(), item.getProtocol(), item.getQuestion(), item.getStatus(),
                        item.getDurationMs(), siteSettingService.formatDateTime(item.getCreateTime())))
                .toList();
        return PageResult.of(records, result.getTotal(), normalizedPage, normalizedSize);
    }

    @Override
    public TraceDetailResponse detail(String traceId) {
        ChatTrace trace = mapper.selectOne(new LambdaQueryWrapper<ChatTrace>()
                .eq(ChatTrace::getTraceId, traceId).last("LIMIT 1"));
        if (trace == null) {
            return null;
        }
        Map<String, Object> node;
        try {
            JsonNode parsed = json.readTree(trace.getTraceJson() == null ? "{}" : trace.getTraceJson());
            node = json.convertValue(parsed, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            node = Map.of();
        }
        return new TraceDetailResponse(trace.getTraceId(), trace.getConversationId(), trace.getUserId(),
                trace.getModelName(), trace.getProtocol(), trace.getQuestion(), trace.getAnswer(), trace.getStatus(),
                trace.getDurationMs(), siteSettingService.formatDateTime(trace.getCreateTime()), node);
    }

    @Override
    public TraceStatsResponse stats() {
        long total = mapper.selectCount(null);
        long success = mapper.selectCount(new LambdaQueryWrapper<ChatTrace>().eq(ChatTrace::getStatus, "SUCCESS"));
        long failed = mapper.selectCount(new LambdaQueryWrapper<ChatTrace>().eq(ChatTrace::getStatus, "FAILED"));
        long truncated = mapper.selectCount(new LambdaQueryWrapper<ChatTrace>().eq(ChatTrace::getStatus, "TRUNCATED"));
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long today = mapper.selectCount(new LambdaQueryWrapper<ChatTrace>().ge(ChatTrace::getCreateTime, startOfDay));
        List<java.util.Map<String, Object>> agg = mapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChatTrace>()
                .select("COALESCE(AVG(duration_ms), 0) AS avg_duration"));
        double avg = 0;
        if (!agg.isEmpty() && agg.get(0) != null && agg.get(0).get("avg_duration") != null) {
            avg = ((Number) agg.get(0).get("avg_duration")).doubleValue();
        }
        return new TraceStatsResponse(total, success, failed, truncated, today, Math.round(avg * 10.0) / 10.0);
    }

    @Override
    public void clearAll() {
        mapper.delete(new LambdaQueryWrapper<ChatTrace>());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
