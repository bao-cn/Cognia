package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.service.ChatService;
import bao.gabriel.cognia.rag.service.RetrievalService;
import bao.gabriel.cognia.rag.service.WebToolService;
import bao.gabriel.cognia.system.dto.chat.ChatMessageRequest;
import bao.gabriel.cognia.system.entity.PromptSetting;
import bao.gabriel.cognia.system.dto.chat.ChatRequest;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.PromptSettingService;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_TOOL_ROUNDS = 3;

    private final ModelConfigService modelConfigService;
    private final RetrievalService retrievalService;
    private final WebToolService webToolService;
    private final PromptSettingService promptSettingService;
    private final ChatConversationService conversationService;
    private final OpenAiCompatibleClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public SseEmitter stream(ChatRequest request, Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.debug("Chat SSE failed: {}", error.getMessage()));
        executor.submit(() -> run(request, userId, emitter));
        return emitter;
    }

    private void run(ChatRequest request, Long userId, SseEmitter emitter) {
        try {
            ResolvedModel model = modelConfigService.resolveChat(request.modelId());
            String lastUser = lastUserMessage(request);
            ChatConversation conversation = conversationService.ensure(request.conversationId(), userId,
                    lastUser, model.id(), request.channelIds());
            if (StringUtils.hasText(lastUser)) {
                conversationService.append(conversation.getPublicId(), userId, "user", lastUser, null);
            }
            send(emitter, event("start").put("model", model.modelId())
                    .put("provider", model.providerName()).put("supportsTools", model.supportsTools())
                    .put("conversationId", conversation.getPublicId()));
            ArrayNode messages = buildMessages(request, userId);
            String effort = model.thinkingEnabled() ? request.effort() : null;
            boolean toolsAvailable = model.supportsTools();
            StringBuilder answer = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                boolean includeTools = toolsAvailable && round < MAX_TOOL_ROUNDS;
                StreamOutcome outcome = streamCompletion(model, messages, effort, emitter, includeTools);
                answer.append(outcome.content());
                reasoning.append(outcome.reasoning());
                if (outcome.toolCalls().isEmpty()) {
                    break;
                }
                ObjectNode assistant = messages.addObject();
                assistant.put("role", "assistant");
                assistant.put("content", outcome.content());
                assistant.set("tool_calls", outcome.toolCalls());
                for (JsonNode call : outcome.toolCalls()) {
                    String id = call.path("id").asText();
                    String name = call.path("function").path("name").asText();
                    String arguments = call.path("function").path("arguments").asText("{}");
                    send(emitter, event("tool").put("id", id).put("name", name).put("status", "running")
                            .put("arguments", arguments));
                    String output;
                    try {
                        output = webToolService.execute(name, arguments, model.apiKey());
                    } catch (Exception ex) {
                        output = "工具执行失败：" + ex.getMessage();
                    }
                    ObjectNode toolMessage = messages.addObject();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", id);
                    toolMessage.put("content", truncate(output, 12000));
                    send(emitter, event("tool").put("id", id).put("name", name).put("status", "done")
                            .put("output", truncate(output, 6000)));
                }
            }
            conversationService.append(conversation.getPublicId(), userId, "assistant",
                    answer.toString(), reasoning.toString());
            send(emitter, event("done"));
            emitter.complete();
        } catch (Exception ex) {
            log.warn("Chat request failed", ex);
            try {
                send(emitter, event("error").put("message", safeMessage(ex)));
            } catch (Exception ignored) {
                // stream already gone
            }
            emitter.completeWithError(ex);
        }
    }

    private String lastUserMessage(ChatRequest request) {
        return request.messages().stream()
                .filter(message -> "user".equals(message.role()))
                .map(ChatMessageRequest::content)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second).orElse("");
    }

    private ArrayNode buildMessages(ChatRequest request, Long userId) {
        ArrayNode messages = mapper.createArrayNode();
        String lastUser = lastUserMessage(request);
        PromptSetting prompt = promptSettingService.getSetting();
        StringBuilder system = new StringBuilder();
        appendPrompt(system, prompt.getSystemPrompt());
        appendPrompt(system, prompt.getRagPrompt());
        appendPrompt(system, prompt.getExpiredPrompt());
        appendPrompt(system, prompt.getToolPrompt());
        if (request.channelIds() != null && !request.channelIds().isEmpty() && StringUtils.hasText(lastUser)) {
            List<RetrievedChunk> chunks = retrievalService.retrieve(lastUser, userId, request.channelIds());
            if (!chunks.isEmpty()) {
                system.append("\n\nRetrieved knowledge:\n");
                int index = 1;
                for (RetrievedChunk chunk : chunks) {
                    system.append("[").append(index++).append("] file=").append(chunk.fileName())
                            .append("; channel=").append(chunk.channelName())
                            .append("; page=").append(chunk.pageNumber())
                            .append("; chunk=").append(chunk.chunkIndex());
                    if (chunk.expired()) {
                        system.append("; 状态：已过期; expiration_time=").append(formatTime(chunk.expirationTime()));
                    }
                    system.append("\n").append(chunk.chunkText()).append("\n\n");
                }
            }
        }
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", system.toString());
        for (ChatMessageRequest message : request.messages()) {
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            String role = normalizeRole(message.role());
            ObjectNode node = messages.addObject();
            node.put("role", role);
            node.put("content", message.content());
        }
        return messages;
    }

    private void appendPrompt(StringBuilder target, String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return;
        }
        if (!target.isEmpty()) {
            target.append('\n');
        }
        target.append(prompt.trim());
    }

    private ObjectNode baseBody(ResolvedModel model, ArrayNode messages, String effort) {
        ObjectNode body = client.mapper().createObjectNode();
        body.put("model", model.modelId());
        body.set("messages", messages);
        body.setAll(modelConfigService.extraBody(model, effort));
        return body;
    }

    private StreamOutcome streamCompletion(ResolvedModel model, ArrayNode messages, String effort,
                                          SseEmitter emitter, boolean includeTools) throws Exception {
        ObjectNode body = baseBody(model, messages, effort);
        body.put("stream", true);
        if (includeTools) {
            body.set("tools", webToolService.toolDefinitions());
            body.put("tool_choice", "auto");
        }
        HttpResponse<InputStream> response = client.stream(model.baseUrl(), "/chat/completions", model.apiKey(), body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(client.describeError(response.statusCode(), error));
        }
        StringBuilder fullText = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        Map<Integer, ObjectNode> toolCalls = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                JsonNode chunk;
                try {
                    chunk = mapper.readTree(payload);
                } catch (Exception ignored) {
                    continue;
                }
                JsonNode delta = chunk.path("choices").path(0).path("delta");
                String reasoning = delta.path("reasoning_content").asText("");
                if (reasoning.isEmpty()) {
                    reasoning = delta.path("reasoning").asText("");
                }
                if (!reasoning.isEmpty()) {
                    fullReasoning.append(reasoning);
                    send(emitter, event("reasoning").put("delta", reasoning));
                }
                String content = delta.path("content").asText("");
                if (!content.isEmpty()) {
                    fullText.append(content);
                    send(emitter, event("text").put("delta", content));
                }
                JsonNode calls = delta.path("tool_calls");
                if (calls.isArray()) {
                    for (JsonNode item : calls) {
                        int index = item.path("index").asInt(toolCalls.size());
                        ObjectNode call = toolCalls.computeIfAbsent(index, key -> {
                            ObjectNode value = mapper.createObjectNode();
                            value.put("id", "call_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                            value.put("type", "function");
                            value.putObject("function").put("name", "");
                            return value;
                        });
                        String id = item.path("id").asText("");
                        if (!id.isEmpty()) {
                            call.put("id", id);
                        }
                        String name = item.path("function").path("name").asText("");
                        if (!name.isEmpty()) {
                            call.with("function").put("name", name);
                        }
                        String arguments = item.path("function").path("arguments").asText("");
                        if (!arguments.isEmpty()) {
                            ObjectNode function = (ObjectNode) call.get("function");
                            function.put("arguments", function.path("arguments").asText("") + arguments);
                        }
                    }
                }
            }
        }
        ArrayNode calls = mapper.createArrayNode();
        for (ObjectNode call : toolCalls.values()) {
            if (StringUtils.hasText(call.path("function").path("name").asText())) {
                calls.add(call);
            }
        }
        log.debug("Chat stream completed with {} chars, {} tool calls", fullText.length(), calls.size());
        return new StreamOutcome(fullText.toString(), fullReasoning.toString(), calls);
    }

    private record StreamOutcome(String content, String reasoning, ArrayNode toolCalls) {
    }

    private void send(SseEmitter emitter, ObjectNode payload) throws Exception {
        emitter.send(SseEmitter.event().data(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
    }

    private ObjectNode event(String type) {
        return mapper.createObjectNode().put("type", type);
    }

    private String normalizeRole(String role) {
        String value = role == null ? "user" : role.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "assistant", "system", "user" -> value;
            default -> "user";
        };
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "\n...[truncated]";
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
