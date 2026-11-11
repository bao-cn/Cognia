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
import java.util.List;
import java.util.Locale;

/**
 * OpenAI Responses API streaming client ({@code POST /responses}). The OpenAI
 * Chat message history is translated to Responses {@code input} items: text
 * becomes {@code input_text}/{@code output_text}, tool calls become separate
 * {@code function_call} items and tool results become
 * {@code function_call_output} items.
 */
@Slf4j
@Component
public class OpenAiResponseProtocolClient implements ChatProtocolClient {

    private static final String PATH = "/responses";
    private static final String FAILED = "请求模型服务失败：";
    private static final int MAX_ERROR_BYTES = 8192;

    private final OpenAiCompatibleClient client;

    public OpenAiResponseProtocolClient(OpenAiCompatibleClient client) {
        this.client = client;
    }

    @Override
    public String protocol() {
        return ModelProvider.PROTOCOL_OPENAI_RESPONSE;
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
        body.set("input", convertInput(request.messages(), mapper));
        body.put("stream", true);
        body.put("max_output_tokens", model.maxOutputTokensOrDefault());
        if (model.thinkingEnabled()) {
            String label = hasText(request.effort()) ? request.effort().trim() : model.reasoningEffort();
            String value = model.reasoningValue(label);
            if (value != null) {
                body.putObject("reasoning").put("effort", value);
            }
        }
        if (request.includeTools() && request.tools() != null && !request.tools().isEmpty()) {
            body.set("tools", convertTools(request.tools(), mapper));
            body.put("tool_choice", "auto");
        }
        mergeExtraBody(body, request.extraBodyJson(), mapper);
        if (request.extraBodyJson() == null || request.extraBodyJson().isBlank()) {
            mergeExtraBody(body, model.extraBody(), mapper);
        }
        return body;
    }

    private ArrayNode convertInput(ArrayNode messages, ObjectMapper mapper) {
        ArrayNode input = mapper.createArrayNode();
        if (messages == null) {
            return input;
        }
        for (JsonNode message : messages) {
            String role = message.path("role").asText("user").toLowerCase(Locale.ROOT);
            switch (role) {
                case "system" -> input.add(textItem("system", "input_text", plainText(message.path("content")), mapper));
                case "assistant" -> {
                    ArrayNode parts = textParts(message.path("content"), "output_text", mapper);
                    if (!parts.isEmpty()) {
                        ObjectNode item = mapper.createObjectNode();
                        item.put("role", "assistant");
                        item.set("content", parts);
                        input.add(item);
                    }
                    appendFunctionCalls(input, message.path("tool_calls"), mapper);
                }
                case "tool" -> {
                    ObjectNode item = mapper.createObjectNode();
                    item.put("type", "function_call_output");
                    item.put("call_id", message.path("tool_call_id").asText(""));
                    item.put("output", plainText(message.path("content")));
                    input.add(item);
                }
                default -> input.add(userItem(message, mapper));
            }
        }
        return input;
    }

    private ObjectNode userItem(JsonNode message, ObjectMapper mapper) {
        ObjectNode item = mapper.createObjectNode();
        item.put("role", "user");
        ArrayNode parts = mapper.createArrayNode();
        JsonNode content = message.path("content");
        if (content.isArray()) {
            for (JsonNode part : content) {
                if ("image_url".equals(part.path("type").asText("")) || part.has("image_url")) {
                    String url = imageUrl(part.path("image_url"));
                    if (!url.isBlank()) {
                        parts.add(mapper.createObjectNode().put("type", "input_image").put("image_url", url));
                    }
                } else {
                    String text = part.path("text").asText("");
                    if (!text.isEmpty()) {
                        parts.add(textPart(text, "input_text", mapper));
                    }
                }
            }
        } else {
            String text = content.asText("");
            if (!text.isEmpty()) {
                parts.add(textPart(text, "input_text", mapper));
            }
        }
        item.set("content", parts);
        return item;
    }

    private ObjectNode textItem(String role, String partType, String text, ObjectMapper mapper) {
        ObjectNode item = mapper.createObjectNode();
        item.put("role", role);
        item.putArray("content").add(textPart(text, partType, mapper));
        return item;
    }

    private ObjectNode textPart(String text, String type, ObjectMapper mapper) {
        return mapper.createObjectNode().put("type", type).put("text", text);
    }

    private ArrayNode textParts(JsonNode content, String type, ObjectMapper mapper) {
        ArrayNode parts = mapper.createArrayNode();
        for (String text : textChunks(content)) {
            if (!text.isEmpty()) {
                parts.add(textPart(text, type, mapper));
            }
        }
        return parts;
    }

    private void appendFunctionCalls(ArrayNode input, JsonNode toolCalls, ObjectMapper mapper) {
        if (toolCalls == null || !toolCalls.isArray()) {
            return;
        }
        for (JsonNode call : toolCalls) {
            JsonNode function = call.path("function");
            ObjectNode item = mapper.createObjectNode();
            item.put("type", "function_call");
            item.put("call_id", call.path("id").asText(""));
            item.put("name", function.path("name").asText(""));
            item.put("arguments", argumentsText(function.path("arguments")));
            input.add(item);
        }
    }

    private ArrayNode convertTools(ArrayNode tools, ObjectMapper mapper) {
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode tool : tools) {
            JsonNode function = tool.has("function") ? tool.path("function") : tool;
            ObjectNode node = mapper.createObjectNode();
            node.put("type", "function");
            node.put("name", function.path("name").asText(""));
            if (function.hasNonNull("description")) {
                node.put("description", function.path("description").asText(""));
            }
            JsonNode parameters = function.path("parameters");
            node.set("parameters", parameters.isObject() ? parameters : mapper.createObjectNode());
            result.add(node);
        }
        return result;
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
        ArrayNode toolCalls = mapper.createArrayNode();
        state.toolCalls.forEach(toolCalls::add);
        return new Result(state.content.toString(), state.reasoning.toString(), toolCalls);
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
        String type = node.path("type").asText("");
        if (type.isEmpty() && hasText(eventName)) {
            type = eventName;
        }
        switch (type) {
            case "response.output_text.delta" -> {
                String delta = node.path("delta").asText("");
                if (!delta.isEmpty()) {
                    state.content.append(delta);
                    listener.onText(delta);
                }
            }
            case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                String delta = node.path("delta").asText("");
                if (!delta.isEmpty()) {
                    state.reasoning.append(delta);
                    listener.onReasoning(delta);
                }
            }
            case "response.output_item.done" -> addFunctionCall(state, node.path("item"), mapper);
            case "response.completed" -> state.done = true;
            case "error" -> throw BusinessException.badRequest("模型服务返回错误：" + errorMessage(node));
            case "response.failed" -> throw BusinessException.badRequest("模型服务返回错误：" + errorMessage(node));
            default -> {
                // ping / in_progress / unknown events are ignored
            }
        }
    }

    private void addFunctionCall(StreamState state, JsonNode item, ObjectMapper mapper) {
        if (!"function_call".equals(item.path("type").asText(""))) {
            return;
        }
        String id = item.path("call_id").asText("");
        if (id.isEmpty()) {
            id = item.path("id").asText("");
        }
        ObjectNode call = mapper.createObjectNode();
        call.put("id", id);
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", item.path("name").asText(""));
        String arguments = item.path("arguments").asText("");
        function.put("arguments", arguments.isBlank() ? "{}" : arguments);
        state.toolCalls.add(call);
    }

    private String errorMessage(JsonNode node) {
        String message = node.path("error").path("message").asText("");
        if (message.isEmpty()) {
            message = node.path("message").asText("");
        }
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

    private String imageUrl(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.isObject() ? value.path("url").asText("") : value.asText("");
    }

    private String argumentsText(JsonNode arguments) {
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            return "{}";
        }
        String text = arguments.isTextual() ? arguments.asText("") : arguments.toString();
        return text.isBlank() ? "{}" : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class StreamState {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final List<ObjectNode> toolCalls = new ArrayList<>();
        private boolean done;
    }
}
