package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.system.entity.MetricEvent;
import bao.gabriel.cognia.system.mapper.MetricEventMapper;
import bao.gabriel.cognia.system.service.MetricEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricEventServiceImpl implements MetricEventService {
    private final MetricEventMapper mapper;

    @Override
    public void recordQa(Long userId, Long channelId, Long conversationId, Long modelId, String modelName,
                         String status, String errorType, long durationMs, boolean retrievalUsed,
                         boolean retrievalHit, boolean rerankUsed, int citationCount, int llmRounds,
                         boolean refused, int inputTokens, int outputTokens, int embeddingTokens,
                         long retrievalDurationMs, long rerankDurationMs) {
        try {
            MetricEvent event = new MetricEvent();
            event.setEventType("QA");
            event.setUserId(userId);
            event.setChannelId(channelId);
            event.setConversationId(conversationId);
            event.setModelId(modelId);
            event.setModelName(modelName);
            event.setStatus(status);
            event.setErrorType(errorType);
            event.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
            event.setRetrievalUsed(retrievalUsed);
            event.setRetrievalHit(retrievalHit);
            event.setRerankUsed(rerankUsed);
            event.setCitationCount(Math.max(0, citationCount));
            event.setLlmRounds(Math.max(0, llmRounds));
            event.setRefused(refused);
            event.setInputTokens(Math.max(0, inputTokens));
            event.setOutputTokens(Math.max(0, outputTokens));
            event.setEmbeddingTokens(Math.max(0, embeddingTokens));
            event.setRetrievalDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, retrievalDurationMs)));
            event.setRerankDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, rerankDurationMs)));
            event.setCreateTime(LocalDateTime.now());
            mapper.insert(event);
        } catch (Exception ex) {
            log.debug("Metric event write skipped: {}", ex.getMessage());
        }
    }
}
