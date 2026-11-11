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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Anthropic Messages API streaming client ({@code POST /messages}). Anthropic
 * authenticates with {@code x-api-key} plus the {@code anthropic-version}
 * header instead of a bearer token, so the header-aware stream overload of
 * {@link OpenAiCompatibleClient} is used.
 */
@Slf4j
@Component
public class AnthropicMessagesProtocolClient implements ChatProtocolClient {

    private static final String PATH = "/messages";
    private static final String FAILED = "请求模型服务失败：";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_ERROR_BYTES = 8192;

    private final OpenAiCompatibleClient client;

    public AnthropicMessagesProtocolClient(OpenAiCompatibleClient client) {
        this.client = client;
    }

    @Override
    public String protocol() {
        return ModelProvider.PROTOCOL_ANTHROPIC_MESSAGES;
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
        HttpResponse<InputStream> response = client.stream(model.baseUrl(), PATH, model.apiKey(), body, headers(model.apiKey()));
        ensureSuccess(response);
        return readStream(response.body(), mapper, listener);
    }

    private Map<String, String> headers(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey.trim());
        }
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        headers.put("content-type", "application/json");
        headers.put("accept", "text/event-stream");
        return headers;
    }

    private ObjectNode buildBody(Request request, ObjectMapper mapper) {
        ResolvedModel model = request.model();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model.modelId());
        body.put("max_tokens", model.maxOutputTokensOrDefault());
        body.put("stream", true);
        String system = systemPrompt(request.messages());
        if (!system.isEmpty()) {
            body.put("system", system);
        }
        body.set("messages", convertMessages(request.messages(), mapper));
        if (model.thinkingEnabled()) {
            int maxTokens = model.maxOutputTokensOrDefault();
            int budget = Math.max(1024, Math.min(maxTokens / 2, 8192));
            ObjectNode thinking = body.putObject("thinking");
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", budget);
        }
        if (request.includeTools() && request.tools() != null && !request.tools().isEmpty()) {
            body.set("tools", convertTools(request.tools(), mapper));
        }
        mergeExtraBody(body, request.extraBodyJson(), mapper);
        if (request.extraBodyJson() == null || request.extraBodyJson().isBlank()) {
            mergeExtraBody(body, model.extraBody(), mapper);
        }
        return body;
    }

    private String systemPrompt(ArrayNode messages) {
        if (messages == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode message : messages) {
            if ("system".equals(message.path("role").asText("").toLowerCase(Locale.ROOT))) {
                String text = plainText(message.path("content"));
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            }
        }
        return String.join("\n\n", parts);
    }

    private ArrayNode convertMessages(ArrayNode messages, ObjectMapper mapper) {
        ArrayNode result = mapper.createArrayNode();
        if (messages == null) {
            return result;
        }
        boolean lastWasTool = false;
        for (JsonNode message : messages) {
            String role = message.path("role").asText("user").toLowerCase(Locale.ROOT);
            if ("system".equals(role)) {
                continue;
            }
            if ("tool".equals(role)) {
                ObjectNode block = mapper.createObjectNode();
                block.put("type", "tool_result");
                block.put("tool_use_id", message.path("tool_call_id").asText(""));
                block.put("content", plainText(message.path("content")));
                if (lastWasTool && !result.isEmpty()
                        && "user".equals(result.get(result.size() - 1).path("role").asText(""))) {
                    ((ArrayNode) result.get(result.size() - 1).path("content")).add(block);
                } else {
                    ObjectNode userMessage = mapper.createObjectNode();
                    userMessage.put("role", "user");
                    userMessage.putArray("content").add(block);
                    result.add(userMessage);
                }
                lastWasTool = true;
                continue;
            }
            ArrayNode blocks = "assistant".equals(role) ? assistantBlocks(message, mapper) : userBlocks(message, mapper);
            lastWasTool = false;
            if (blocks.isEmpty()) {
                continue;
            }
            ObjectNode item = mapper.createObjectNode();
            item.put("role", "assistant".equals(role) ? "assistant" : "user");
            item.set("content", blocks);
            result.add(item);
        }
        return result;
    }

    private ArrayNode userBlocks(JsonNode message, ObjectMapper mapper) {
        ArrayNode blocks = mapper.createArrayNode();
        JsonNode content = message.path("content");
        if (content.isArray()) {
            for (JsonNode part : content) {
                if ("image_url".equals(part.path("type").asText("")) || part.has("image_url")) {
                    ObjectNode image = imageBlock(part.path("image_url"), mapper);
                    if (image != null) {
                        blocks.add(image);
                    }
                } else {
                    String text = part.path("text").asText("");
                    if (!text.isEmpty()) {
                        blocks.add(mapper.createObjectNode().put("type", "text").put("text", text));
                    }
                }
            }
        } else {
            String text = content.asText("");
            if (!text.isEmpty()) {
                blocks.add(mapper.createObjectNode().put("type", "text").put("text", text));
            }
        }
        return blocks;
    }

    private ArrayNode assistantBlocks(JsonNode message, ObjectMapper mapper) {
        ArrayNode blocks = mapper.createArrayNode();
        for (String text : textChunks(message.path("content"))) {
            if (!text.isEmpty()) {
                blocks.add(mapper.createObjectNode().put("type", "text").put("text", text));
            }
        }
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode call : calls) {
                JsonNode function = call.path("function");
                String id = call.path("id").asText("");
                if (id.isBlank()) {
                    id = "toolu_" + UUID.randomUUID().toString().replace("-", "");
                }
                ObjectNode toolUse = mapper.createObjectNode();
                toolUse.put("type", "tool_use");
                toolUse.put("id", id);
                toolUse.put("name", function.path("name").asText(""));
                toolUse.set("input", parseArguments(function.path("arguments"), mapper));
                blocks.add(toolUse);
            }
        }
        return blocks;
    }

    private ObjectNode imageBlock(JsonNode value, ObjectMapper mapper) {
        String url = value == null || value.isMissingNode() || value.isNull()
                ? "" : (value.isObject() ? value.path("url").asText("") : value.asText(""));
        if (url.isBlank()) {
            return null;
        }
        ObjectNode image = mapper.createObjectNode();
        image.put("type", "image");
        ObjectNode source = image.putObject("source");
        if (url.startsWith("data:")) {
            int semi = url.indexOf(';');
            int comma = url.indexOf(',');
            if (semi < 0 || comma < 0 || semi > comma) {
                return null;
            }
            String mediaType = url.substring(5, semi);
            source.put("type", "base64");
            source.put("media_type", mediaType.isBlank() ? "image/png" : mediaType);
            source.put("data", url.substring(comma + 1));
        } else {
            source.put("type", "url");
            source.put("url", url);
        }
        return image;
    }

    private ArrayNode convertTools(ArrayNode tools, ObjectMapper mapper) {
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode tool : tools) {
            JsonNode function = tool.has("function") ? tool.path("function") : tool;
            ObjectNode node = mapper.createObjectNode();
            node.put("name", function.path("name").asText(""));
            if (function.hasNonNull("description")) {
                node.put("description", function.path("description").asText(""));
            }
            JsonNode parameters = function.path("parameters");
            node.set("input_schema", parameters.isObject() ? parameters : defaultSchema(mapper));
            result.add(node);
        }
        return result;
    }

    private ObjectNode defaultSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    private Result readStream(InputStream stream, ObjectMapper mapper, Listener listener) {
        StreamState state = new StreamState();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder data = new StringBuilder();
            String line;
            while (!state.done && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (data.length() > 0) {
                        handle(state, eventName, data.toString(), mapper, listener);
                        data.setLength(0);
                    }
                    eventName = null;
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
            if (!state.done && data.length() > 0) {
                handle(state, eventName, data.toString(), mapper, listener);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(FAILED + ex.getMessage());
        }
        return new Result(state.content.toString(), state.reasoning.toString(), assembleCalls(state, mapper));
    }

    private void handle(StreamState state, String eventName, String payload,
                        ObjectMapper mapper, Listener listener) throws Exception {
        if (payload.isEmpty()) {
            return;
        }
        if ("[DONE]".equals(payload)) {
            state.done = true;
            return;
        }
        JsonNode node;
        try {
            node = mapper.readTree(payload);
        } catch (Exception ignored) {
            return;
        }
        listener.onRawChunk(payload);
        String type = hasText(eventName) ? eventName : node.path("type").asText("");
        switch (type) {
            case "content_block_start" -> startBlock(state, node);
            case "content_block_delta" -> delta(state, node, listener);
            case "message_stop" -> state.done = true;
            case "error" -> throw BusinessException.badRequest("模型服务返回错误：" + errorMessage(node));
            default -> {
                // message_start / message_delta / content_block_stop / ping
            }
        }
    }

    private void startBlock(StreamState state, JsonNode node) {
        JsonNode block = node.path("content_block");
        if (!"tool_use".equals(block.path("type").asText(""))) {
            return;
        }
        ToolCall call = new ToolCall();
        call.id = block.path("id").asText("");
        call.name = block.path("name").asText("");
        state.toolCalls.put(node.path("index").asInt(state.toolCalls.size()), call);
    }

    private void delta(StreamState state, JsonNode node, Listener listener) throws Exception {
        JsonNode delta = node.path("delta");
        String type = delta.path("type").asText("");
        switch (type) {
            case "text_delta" -> {
                String text = delta.path("text").asText("");
                if (!text.isEmpty()) {
                    state.content.append(text);
                    listener.onText(text);
                }
            }
            case "thinking_delta" -> {
                String thinking = delta.path("thinking").asText("");
                if (!thinking.isEmpty()) {
                    state.reasoning.append(thinking);
                    listener.onReasoning(thinking);
                }
            }
            case "input_json_delta" -> {
                ToolCall call = state.toolCalls.get(node.path("index").asInt(-1));
                if (call != null) {
                    call.arguments.append(delta.path("partial_json").asText(""));
                }
            }
            default -> {
                // signature_delta and future delta types are ignored
            }
        }
    }

    private ArrayNode assembleCalls(StreamState state, ObjectMapper mapper) {
        ArrayNode result = mapper.createArrayNode();
        for (ToolCall call : state.toolCalls.values()) {
            if (call.name.isBlank()) {
                continue;
            }
            ObjectNode node = mapper.createObjectNode();
            node.put("id", call.id.isBlank() ? "toolu_" + UUID.randomUUID().toString().replace("-", "") : call.id);
            node.put("type", "function");
            ObjectNode function = node.putObject("function");
            function.put("name", call.name);
            function.put("arguments", call.arguments.isEmpty() ? "{}" : call.arguments.toString());
            result.add(node);
        }
        return result;
    }

    private JsonNode parseArguments(JsonNode arguments, ObjectMapper mapper) {
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            return mapper.createObjectNode();
        }
        if (arguments.isObject()) {
            return arguments;
        }
        String raw = arguments.isTextual() ? arguments.asText("") : arguments.toString();
        if (raw.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode parsed = mapper.readTree(raw);
            return parsed != null && parsed.isObject() ? parsed : mapper.createObjectNode();
        } catch (Exception ex) {
            return mapper.createObjectNode();
        }
    }

    private String errorMessage(JsonNode node) {
        String message = node.path("error").path("message").asText("");
        return message.isEmpty() ? "模型服务返回错误" : message;
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

    private String plainText(JsonNode content) {
        return String.join("", textChunks(content));
    }

    private List<String> textChunks(JsonNode content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isMissingNode() || content.isNull()) {
            return chunks;
        }
        if (content.isArray()) {
            for (JsonNode part : content) {
                String text = part.isTextual() ? part.asText() : part.path("text").asText("");
                if (!text.isEmpty()) {
                    chunks.add(text);
                }
            }
        } else {
            String text = content.asText("");
            if (!text.isEmpty()) {
                chunks.add(text);
            }
        }
        return chunks;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ToolCall {
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }

    private static final class StreamState {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, ToolCall> toolCalls = new TreeMap<>();
        private boolean done;
    }
}
