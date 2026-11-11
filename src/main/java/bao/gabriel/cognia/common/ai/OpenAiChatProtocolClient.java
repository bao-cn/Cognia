package bao.gabriel.cognia.common.ai;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.entity.ModelProvider;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * OpenAI Chat Completions streaming client. Also covers every vendor that
 * exposes an OpenAI-compatible {@code /chat/completions} endpoint, including
 * the vendor specific thinking switches used by DeepSeek and Qwen.
 */
@Slf4j
@Component
public class OpenAiChatProtocolClient implements ChatProtocolClient {

    private static final String PATH = "/chat/completions";
    private static final String FAILED = "请求模型服务失败：";
    private static final int MAX_ERROR_BYTES = 8192;

    private final OpenAiCompatibleClient client;

    public OpenAiChatProtocolClient(OpenAiCompatibleClient client) {
        this.client = client;
    }

    @Override
    public String protocol() {
        return ModelProvider.PROTOCOL_OPENAI_CHAT;
    }

    @Override
    public Result stream(Request request, Listener listener) {
        ResolvedModel model = request.model();
        ObjectMapper mapper = client.mapper();
        ObjectNode body = buildBody(request, mapper);
        try {
            listener.onRequest(body);
        } catch (Exception ignored) {
            // tracing hooks must never break the stream
        }
        HttpResponse<InputStream> response = client.stream(model.baseUrl(), PATH, model.apiKey(), body);
        ensureSuccess(response);
        return readStream(response.body(), mapper, listener);
    }

    private ObjectNode buildBody(Request request, ObjectMapper mapper) {
        ResolvedModel model = request.model();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model.modelId());
        body.set("messages", request.messages() == null ? mapper.createArrayNode() : request.messages());
        body.put("stream", true);
        body.put("max_tokens", model.maxOutputTokensOrDefault());
        applyReasoning(body, model, request.effort());
        if (request.includeTools() && request.tools() != null && !request.tools().isEmpty()) {
            body.set("tools", request.tools());
            body.put("tool_choice", "auto");
        }
        mergeExtraBody(body, request.extraBodyJson(), mapper);
        if (request.extraBodyJson() == null || request.extraBodyJson().isBlank()) {
            mergeExtraBody(body, model.extraBody(), mapper);
        }
        return body;
    }

    /**
     * Adds the vendor specific thinking switches. Only enabled thinking is sent;
     * a disabled model simply omits the vendor parameters.
     */
    private void applyReasoning(ObjectNode body, ResolvedModel model, String effort) {
        if (!model.thinkingEnabled()) {
            return;
        }
        String label = hasText(effort) ? effort.trim() : model.reasoningEffort();
        String value = model.reasoningValue(label);
        String vendor = model.vendor() == null ? "" : model.vendor().trim().toUpperCase(Locale.ROOT);
        switch (vendor) {
            case "DEEPSEEK" -> body.putObject("thinking").put("type", "enabled");
            case "QWEN" -> body.put("enable_thinking", true);
            default -> {
                // generic OpenAI-compatible vendors only understand reasoning_effort
            }
        }
        if (value != null) {
            body.put("reasoning_effort", value);
        }
    }

    private Result readStream(InputStream stream, ObjectMapper mapper, Listener listener) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        Map<Integer, ToolCall> calls = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String payload = dataPayload(line);
                if (payload == null || payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                JsonNode chunk;
                try {
                    chunk = mapper.readTree(payload);
                } catch (Exception ignored) {
                    continue;
                }
                listener.onRawChunk(payload);
                JsonNode choice = chunk.path("choices").path(0);
                JsonNode delta = choice.path("delta");
                String text = delta.path("content").asText("");
                if (!text.isEmpty()) {
                    content.append(text);
                    listener.onText(text);
                }
                String thinking = delta.path("reasoning_content").asText("");
                if (thinking.isEmpty()) {
                    thinking = delta.path("reasoning").asText("");
                }
                if (!thinking.isEmpty()) {
                    reasoning.append(thinking);
                    listener.onReasoning(thinking);
                }
                collectToolCalls(delta.path("tool_calls"), calls);
                if (!choice.path("finish_reason").asText("").isEmpty()) {
                    break;
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(FAILED + ex.getMessage());
        }
        return new Result(content.toString(), reasoning.toString(), assembleCalls(calls, mapper));
    }

    private void collectToolCalls(JsonNode items, Map<Integer, ToolCall> calls) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            int index = item.path("index").asInt(calls.size());
            ToolCall call = calls.computeIfAbsent(index, key -> new ToolCall());
            String id = item.path("id").asText("");
            if (!id.isEmpty()) {
                call.id = id;
            }
            String name = item.path("function").path("name").asText("");
            if (!name.isEmpty()) {
                call.name = name;
            }
            call.arguments.append(item.path("function").path("arguments").asText(""));
        }
    }

    private ArrayNode assembleCalls(Map<Integer, ToolCall> calls, ObjectMapper mapper) {
        ArrayNode result = mapper.createArrayNode();
        for (ToolCall call : calls.values()) {
            if (call.name.isBlank()) {
                continue;
            }
            ObjectNode node = mapper.createObjectNode();
            node.put("id", call.id.isBlank() ? "call_" + UUID.randomUUID().toString().replace("-", "") : call.id);
            node.put("type", "function");
            ObjectNode function = node.putObject("function");
            function.put("name", call.name);
            function.put("arguments", call.arguments.isEmpty() ? "{}" : call.arguments.toString());
            result.add(node);
        }
        return result;
    }

    private void mergeExtraBody(ObjectNode body, String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode extra = mapper.readTree(json);
            if (extra.isObject()) {
                extra.fields().forEachRemaining(entry -> body.set(entry.getKey(), entry.getValue()));
            }
        } catch (Exception ex) {
            log.warn("Ignoring invalid extra body JSON: {}", ex.getMessage());
        }
    }

    private void ensureSuccess(HttpResponse<InputStream> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw BusinessException.badRequest(client.describeError(response.statusCode(), readBounded(response.body())));
    }

    private String readBounded(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            return new String(in.readNBytes(MAX_ERROR_BYTES), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    /** Returns the payload of an SSE {@code data:} line, {@code null} otherwise. */
    private String dataPayload(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.startsWith("data:") ? trimmed.substring(5).trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ToolCall {
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }
}
