package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.ChatProtocolClient;
import bao.gabriel.cognia.common.ai.ChatProtocolDispatcher;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.RetrievalService;
import bao.gabriel.cognia.rag.service.TraceReplayService;
import bao.gabriel.cognia.system.dto.trace.ReplayResponse;
import bao.gabriel.cognia.system.entity.ChatTrace;
import bao.gabriel.cognia.system.mapper.ChatTraceMapper;
import bao.gabriel.cognia.system.service.ModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceReplayServiceImpl implements TraceReplayService {

    private final ChatTraceMapper chatTraceMapper;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final ModelConfigService modelConfigService;
    private final ChatProtocolDispatcher chatProtocolDispatcher;

    @Override
    public ReplayResponse replay(String traceId, String stage, Long operatorId) {
        ChatTrace trace = requireTrace(traceId);
        JsonNode root;
        try {
            root = mapper.readTree(trace.getTraceJson());
        } catch (Exception ex) {
            throw BusinessException.badRequest("链路记录内容损坏，无法重放");
        }
        long started = System.nanoTime();
        if ("retrieval".equalsIgnoreCase(stage)) {
            return replayRetrieval(trace, root, started, operatorId);
        }
        if ("llm".equalsIgnoreCase(stage)) {
            return replayLlm(trace, root, started, operatorId);
        }
        throw BusinessException.badRequest("不支持的重放节点：" + stage);
    }

    private ReplayResponse replayRetrieval(ChatTrace trace, JsonNode root, long started, Long operatorId) {
        String query = root.path("input").path("finalQuery").asText(root.path("input").path("rawQuery").asText(""));
        if (!StringUtils.hasText(query)) {
            throw BusinessException.badRequest("链路记录中没有可重放的检索 query");
        }
        List<Long> channels = new ArrayList<>();
        for (JsonNode channel : root.path("meta").path("channels")) {
            if (channel.isNumber()) {
                channels.add(channel.asLong());
            }
        }
        EmbeddingService.EmbeddedText embedded = embeddingService.embed(query);
        List<RetrievedChunk> chunks = retrievalService.retrieve(query, embedded.vector(), trace.getUserId(), channels);
        ObjectNode result = mapper.createObjectNode();
        result.put("query", query);
        result.put("count", chunks.size());
        ArrayNode items = result.putArray("chunks");
        for (RetrievedChunk chunk : chunks) {
            ObjectNode item = items.addObject();
            item.put("documentId", chunk.documentId());
            item.put("fileName", chunk.fileName());
            item.put("chunkIndex", chunk.chunkIndex());
            item.put("score", chunk.score());
            item.put("text", chunk.chunkText());
        }
        long duration = (System.nanoTime() - started) / 1_000_000;
        log.info("Trace retrieval replay {} by {} took {}ms", trace.getTraceId(), operatorId, duration);
        return new ReplayResponse(trace.getTraceId(), "retrieval", "SUCCESS", duration, mapper.convertValue(result, Map.class),
                "已使用当前检索参数重跑，共召回 " + chunks.size() + " 个片段");
    }

    private ReplayResponse replayLlm(ChatTrace trace, JsonNode root, long started, Long operatorId) {
        JsonNode messagesNode = root.path("prompt").path("messages");
        if (!messagesNode.isArray() || messagesNode.isEmpty()) {
            throw BusinessException.badRequest("链路记录中没有可重放的 prompt");
        }
        long modelId = root.path("meta").path("modelId").asLong(0);
        if (modelId <= 0) {
            throw BusinessException.badRequest("链路记录中没有模型信息，无法重放 LLM");
        }
        var model = modelConfigService.resolveModel(modelId);
        ArrayNode messages = (ArrayNode) messagesNode;
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        ChatProtocolClient.Result result = chatProtocolDispatcher.stream(model, messages, null, false,
                mapper.createArrayNode(), new ChatProtocolClient.Listener() {
                    @Override
                    public void onReasoning(String delta) {
                        reasoning.append(delta);
                    }

                    @Override
                    public void onText(String delta) {
                        text.append(delta);
                    }

                    @Override
                    public void onRawChunk(String json) {
                        // replay keeps only the assembled answer
                    }
                });
        ObjectNode payload = mapper.createObjectNode();
        payload.put("content", text.toString());
        payload.put("reasoning", reasoning.toString());
        payload.set("toolCalls", result == null || result.toolCalls() == null
                ? mapper.createArrayNode() : result.toolCalls());
        long duration = (System.nanoTime() - started) / 1_000_000;
        log.info("Trace llm replay {} by {} took {}ms", trace.getTraceId(), operatorId, duration);
        return new ReplayResponse(trace.getTraceId(), "llm", "SUCCESS", duration, mapper.convertValue(payload, Map.class),
                "已使用记录的 prompt 重跑模型");
    }

    private ChatTrace requireTrace(String traceId) {
        ChatTrace trace = chatTraceMapper.selectOne(new LambdaQueryWrapper<ChatTrace>()
                .eq(ChatTrace::getTraceId, traceId)
                .last("LIMIT 1"));
        if (trace == null) {
            throw BusinessException.notFound("链路记录不存在或已过期");
        }
        return trace;
    }
}
