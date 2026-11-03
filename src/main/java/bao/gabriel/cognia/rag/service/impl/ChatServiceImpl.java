package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.service.ChatService;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.RagCacheService;
import bao.gabriel.cognia.rag.service.RetrievalService;
import bao.gabriel.cognia.rag.service.WebToolService;
import bao.gabriel.cognia.system.dto.chat.ChatAttachmentRequest;
import bao.gabriel.cognia.system.dto.chat.ChatMessageRequest;
import bao.gabriel.cognia.system.entity.PromptSetting;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.dto.chat.ChatRequest;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.PromptSettingService;
import bao.gabriel.cognia.system.service.RagSettingService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int MAX_TOOL_ROUNDS = 3;
    private static final String HIT_NONE = "NONE";
    private static final String HIT_SEMANTIC = "SEMANTIC";
    private static final String HIT_RETRIEVAL = "RETRIEVAL";

    private final ModelConfigService modelConfigService;
    private final RetrievalService retrievalService;
    private final WebToolService webToolService;
    private final PromptSettingService promptSettingService;
    private final ChatConversationService conversationService;
    private final ChannelService channelService;
    private final RagSettingService ragSettingService;
    private final EmbeddingService embeddingService;
    private final RagCacheService ragCacheService;
    private final ContextWindowManager contextWindowManager;
    private final OpenAiCompatibleClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    /**
     * Per-key locks preventing a cache stampede for identical questions. Keys are
     * cache keys (scope + normalized query).
     */
    private final ConcurrentHashMap<String, ReentrantLock> cacheLocks = new ConcurrentHashMap<>();

    @Override
    public SseEmitter stream(ChatRequest request, Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.debug("Chat SSE failed: {}", error.getMessage()));
        executor.submit(() -> run(request, userId, emitter));
        return emitter;
    }

    private void run(ChatRequest request, Long userId, SseEmitter emitter) {
        ReentrantLock stampedeLock = null;
        try {
            ResolvedModel model = modelConfigService.resolveChat(request.modelId());
            RagSetting setting = ragSettingService.getSetting();
            List<ChatAttachmentRequest> attachments = attachments(request);
            boolean hasImageAttachment = attachments.stream()
                    .anyMatch(item -> "IMAGE".equalsIgnoreCase(item.type()));
            if (!attachments.isEmpty() && !model.supportsVision()) {
                throw bao.gabriel.cognia.common.exception.BusinessException.badRequest("当前模型不支持文件上传，请选择支持视觉的模型");
            }
            String lastUser = lastUserMessage(request);
            boolean temporary = Boolean.TRUE.equals(request.temporary());
            boolean newConversation = !temporary && !StringUtils.hasText(request.conversationId());
            ChatConversation conversation = null;
            String conversationPublicId = "";
            if (!temporary) {
                conversation = conversationService.ensure(request.conversationId(), userId,
                        lastUser, model.id(), request.channelIds());
                conversationPublicId = conversation.getPublicId();
                if (StringUtils.hasText(lastUser)) {
                    conversationService.append(conversationPublicId, userId, "user", lastUser, null);
                }
            }
            send(emitter, event("start").put("model", model.modelId())
                    .put("provider", model.providerName()).put("supportsTools", model.supportsTools())
                    .put("supportsVision", model.supportsVision())
                    .put("conversationId", conversationPublicId));

            ragCacheService.recordRequest();
            boolean semanticEnabled = setting.semanticCacheEnabled() && !hasImageAttachment;
            boolean retrievalEnabled = setting.retrievalCacheEnabled();
            boolean hasChannels = request.channelIds() != null && !request.channelIds().isEmpty();
            String scopeHash = ragCacheService.scopeHash(userId, request.channelIds());
            String cacheKey = StringUtils.hasText(lastUser)
                    ? ragCacheService.cacheKey(scopeHash, lastUser) : scopeHash;

            float[] queryVector = null;
            List<RetrievedChunk> chunks = null;
            String hitType = HIT_NONE;
            double similarity = 0;
            String cachedAnswer = null;

            if (StringUtils.hasText(lastUser)) {
                if (semanticEnabled) {
                    queryVector = embedOrNull(lastUser);
                    RagCacheService.SemanticHit hit = queryVector == null ? null
                            : ragCacheService.findSemantic(scopeHash, lastUser, queryVector, setting);
                    if (hit != null) {
                        hitType = HIT_SEMANTIC;
                        similarity = hit.similarity();
                        cachedAnswer = hit.answer();
                    }
                }
                if (HIT_NONE.equals(hitType) && retrievalEnabled) {
                    RagCacheService.RetrievalHit hit = ragCacheService.findRetrieval(scopeHash, lastUser, setting);
                    if (hit != null) {
                        hitType = HIT_RETRIEVAL;
                        chunks = hit.chunks();
                    }
                }
            }

            // Stampede protection: on a miss, serialize identical questions and
            // re-check once the lock is held (another request may have filled it).
            boolean cacheActive = semanticEnabled || retrievalEnabled;
            if (HIT_NONE.equals(hitType) && cacheActive && StringUtils.hasText(lastUser)) {
                stampedeLock = cacheLocks.computeIfAbsent(cacheKey, key -> new ReentrantLock());
                stampedeLock.lock();
                if (semanticEnabled && queryVector != null) {
                    RagCacheService.SemanticHit hit =
                            ragCacheService.findSemantic(scopeHash, lastUser, queryVector, setting);
                    if (hit != null) {
                        hitType = HIT_SEMANTIC;
                        similarity = hit.similarity();
                        cachedAnswer = hit.answer();
                    }
                }
                if (HIT_NONE.equals(hitType) && retrievalEnabled) {
                    RagCacheService.RetrievalHit hit = ragCacheService.findRetrieval(scopeHash, lastUser, setting);
                    if (hit != null) {
                        hitType = HIT_RETRIEVAL;
                        chunks = hit.chunks();
                    }
                }
            }

            if (HIT_SEMANTIC.equals(hitType) && cachedAnswer != null) {
                ragCacheService.recordHit(HIT_SEMANTIC);
                send(emitter, cacheEvent(hitType, similarity, cacheKey));
                // Cached answers do not build a prompt, but the client still
                // expects a context event for its usage ring.
                send(emitter, contextEvent(emptyContext(model, setting)));
                if (conversation != null) {
                    conversationService.append(conversationPublicId, userId, "assistant", cachedAnswer, "");
                    if (newConversation) {
                        conversationService.generateTitleAsync(conversationPublicId, model.id(), lastUser, cachedAnswer);
                    }
                }
                send(emitter, event("text").put("delta", cachedAnswer));
                send(emitter, doneEvent(hitType, similarity, cacheKey));
                emitter.complete();
                return;
            }

            if (HIT_NONE.equals(hitType)) {
                ragCacheService.recordMiss();
            } else {
                ragCacheService.recordHit(hitType);
            }
            send(emitter, cacheEvent(hitType, similarity, cacheKey));

            // Resolve retrieval chunks once: cached chunks skip the embedding and
            // vector search entirely, a miss is written back asynchronously.
            if (chunks == null) {
                if (hasChannels && StringUtils.hasText(lastUser)) {
                    chunks = queryVector != null
                            ? retrievalService.retrieve(lastUser, queryVector, userId, request.channelIds())
                            : retrievalService.retrieve(lastUser, userId, request.channelIds());
                    if (retrievalEnabled) {
                        ragCacheService.storeRetrievalAsync(scopeHash, lastUser, chunks, setting);
                    }
                } else {
                    chunks = List.of();
                }
            }

            ArrayNode messages = buildMessages(request, userId, chunks);
            ContextWindowManager.Prepared prepared = contextWindowManager.prepare(messages, model, setting);
            send(emitter, contextEvent(prepared));

            String effort = model.thinkingEnabled() ? request.effort() : null;
            boolean toolsAvailable = model.supportsTools() && webSearchAllowed(setting, request);
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
            if (conversation != null) {
                conversationService.append(conversation.getPublicId(), userId, "assistant",
                        answer.toString(), reasoning.toString());
                if (newConversation) {
                    conversationService.generateTitleAsync(conversation.getPublicId(), model.id(),
                            lastUser, answer.toString());
                }
            }
            // Best-effort write; never blocks or fails the streamed response.
            if (semanticEnabled && queryVector != null && !answer.isEmpty()
                    && StringUtils.hasText(lastUser)) {
                try {
                    ragCacheService.storeSemanticAsync(scopeHash, lastUser, answer.toString(),
                            contextJson(chunks), queryVector, setting);
                } catch (Exception ex) {
                    log.warn("Semantic cache write skipped: {}", ex.getMessage());
                }
            }
            send(emitter, doneEvent(hitType, similarity, cacheKey));
            emitter.complete();
        } catch (Exception ex) {
            log.warn("Chat request failed", ex);
            try {
                send(emitter, event("error").put("message", safeMessage(ex)));
            } catch (Exception ignored) {
                // stream already gone
            }
            emitter.completeWithError(ex);
        } finally {
            if (stampedeLock != null && stampedeLock.isHeldByCurrentThread()) {
                stampedeLock.unlock();
            }
        }
    }

    private float[] embedOrNull(String query) {
        try {
            EmbeddingService.EmbeddedText embedded = embeddingService.embed(query);
            return embedded == null ? null : embedded.vector();
        } catch (Exception ex) {
            log.warn("Query embedding failed, semantic cache skipped: {}", ex.getMessage());
            return null;
        }
    }

    private ObjectNode cacheEvent(String hitType, double similarity, String cacheKey) {
        RagCacheService.Stats stats = ragCacheService.stats();
        return event("cache")
                .put("hitType", hitType)
                .put("similarity", round(similarity))
                .put("cacheKey", cacheKey)
                .put("hits", stats.hits())
                .put("requests", stats.requests())
                .put("hitRate", stats.hitRate());
    }

    private ContextWindowManager.Prepared emptyContext(ResolvedModel model, RagSetting setting) {
        int maxTokens = model.contextWindowOrDefault();
        double threshold = Math.min(100, Math.max(1, setting.contextCompressionThreshold())) / 100.0;
        return new ContextWindowManager.Prepared(mapper.createArrayNode(), 0, maxTokens, threshold, false, 0);
    }

    private ObjectNode contextEvent(ContextWindowManager.Prepared prepared) {
        int percent = prepared.maxTokens() <= 0 ? 0
                : (int) Math.round(prepared.usedTokens() * 100.0 / prepared.maxTokens());
        int thresholdPercent = (int) Math.round(prepared.compressionThreshold() * 100);
        return event("context")
                .put("usedTokens", prepared.usedTokens())
                .put("maxTokens", prepared.maxTokens())
                .put("percent", percent)
                .put("compressionThreshold", thresholdPercent)
                .put("compressed", prepared.compressed())
                .put("droppedMessages", prepared.droppedMessages());
    }

    private ObjectNode doneEvent(String hitType, double similarity, String cacheKey) {
        return event("done")
                .put("hitType", hitType)
                .put("similarity", round(similarity))
                .put("cacheKey", cacheKey);
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String contextJson(List<RetrievedChunk> chunks) {
        ArrayNode context = mapper.createArrayNode();
        for (RetrievedChunk chunk : chunks) {
            ObjectNode node = context.addObject();
            node.put("documentId", chunk.documentId());
            node.put("channelId", chunk.channelId());
            node.put("fileName", chunk.fileName());
            node.put("chunkIndex", chunk.chunkIndex());
            node.put("pageNumber", chunk.pageNumber());
            node.put("score", chunk.score());
            node.put("text", truncate(chunk.chunkText(), 500));
        }
        return context.toString();
    }

    /**
     * Web search is only exposed when the platform enables it, the user did not
     * opt out (when opting out is allowed) and every selected channel allows it.
     */
    private boolean webSearchAllowed(RagSetting setting, ChatRequest request) {
        if (!setting.webSearchEnabled()) {
            return false;
        }
        if (setting.allowUserWebSearch() && Boolean.FALSE.equals(request.webSearch())) {
            return false;
        }
        if (request.channelIds() != null) {
            for (Long channelId : request.channelIds()) {
                if (!channelService.webSearchEnabled(channelId)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String lastUserMessage(ChatRequest request) {
        String content = request.messages().stream()
                .filter(message -> "user".equals(message.role()))
                .map(ChatMessageRequest::content)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second).orElse("");
        String extra = textAttachments(attachments(request));
        String combined = StringUtils.hasText(extra) ? content + "\n\n" + extra : content;
        if (!StringUtils.hasText(combined) && attachments(request).stream()
                .anyMatch(item -> "IMAGE".equalsIgnoreCase(item.type()))) {
            return "[图片消息]";
        }
        return combined;
    }

    private List<ChatAttachmentRequest> attachments(ChatRequest request) {
        return request.attachments() == null ? List.of() : request.attachments();
    }

    private String textAttachments(List<ChatAttachmentRequest> attachments) {
        StringBuilder text = new StringBuilder();
        for (ChatAttachmentRequest attachment : attachments) {
            if (!"TEXT".equalsIgnoreCase(attachment.type()) || !StringUtils.hasText(attachment.text())) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("[附件：").append(attachment.name() == null ? "document" : attachment.name())
                    .append("]\n").append(attachment.text());
        }
        return text.toString();
    }

    private ArrayNode buildMessages(ChatRequest request, Long userId, List<RetrievedChunk> chunks) {
        ArrayNode messages = mapper.createArrayNode();
        PromptSetting prompt = promptSettingService.getSetting();
        StringBuilder system = new StringBuilder();
        appendPrompt(system, prompt.getSystemPrompt());
        appendPrompt(system, prompt.getRagPrompt());
        appendPrompt(system, prompt.getExpiredPrompt());
        appendPrompt(system, prompt.getToolPrompt());
        if (request.channelIds() != null && !request.channelIds().isEmpty()) {
            String agentPrompt = channelService.agentPrompt(request.channelIds(), userId);
            if (StringUtils.hasText(agentPrompt)) {
                system.append("\n\nChannel AGENT knowledge:\n").append(agentPrompt);
            }
        }
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
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", system.toString());
        List<ChatAttachmentRequest> attachments = attachments(request);
        List<String> imageUrls = new ArrayList<>();
        for (ChatAttachmentRequest attachment : attachments) {
            if ("IMAGE".equalsIgnoreCase(attachment.type()) && StringUtils.hasText(attachment.dataUrl())) {
                imageUrls.add(attachment.dataUrl());
            }
        }
        String extraText = textAttachments(attachments);
        List<ChatMessageRequest> source = request.messages();
        int lastUserIndex = -1;
        for (int i = 0; i < source.size(); i++) {
            if ("user".equalsIgnoreCase(source.get(i).role())) {
                lastUserIndex = i;
            }
        }
        for (int i = 0; i < source.size(); i++) {
            ChatMessageRequest message = source.get(i);
            String content = message.content() == null ? "" : message.content();
            if (i == lastUserIndex && StringUtils.hasText(extraText)) {
                content = content + (StringUtils.hasText(content) ? "\n\n" : "") + extraText;
            }
            boolean lastUser = i == lastUserIndex;
            if (content.isBlank() && !(lastUser && !imageUrls.isEmpty())) {
                continue;
            }
            String role = normalizeRole(message.role());
            ObjectNode node = messages.addObject();
            node.put("role", role);
            if (lastUser && !imageUrls.isEmpty()) {
                ArrayNode parts = node.putArray("content");
                parts.addObject().put("type", "text").put("text", content);
                for (String url : imageUrls) {
                    ObjectNode part = parts.addObject();
                    part.put("type", "image_url");
                    part.putObject("image_url").put("url", url);
                }
            } else {
                node.put("content", content);
            }
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
