package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.ai.StreamingTextSanitizer;
import bao.gabriel.cognia.rag.dto.CitationSource;
import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.service.ChatService;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.common.ai.ChatProtocolClient;
import bao.gabriel.cognia.common.ai.ChatProtocolDispatcher;
import bao.gabriel.cognia.rag.service.ChatTraceService;
import bao.gabriel.cognia.rag.service.DebugSettingService;
import bao.gabriel.cognia.rag.service.RagCacheService;
import bao.gabriel.cognia.rag.service.trace.TraceRecorder;
import bao.gabriel.cognia.rag.service.RetrievalService;
import bao.gabriel.cognia.rag.service.WebToolService;
import bao.gabriel.cognia.system.dto.chat.ChatAttachmentRequest;
import bao.gabriel.cognia.system.dto.chat.ChatMessageRequest;
import bao.gabriel.cognia.system.dto.chat.ConversationMessageResponse;
import bao.gabriel.cognia.system.entity.PromptSetting;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.dto.chat.ChatRequest;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.MetricEventService;
import bao.gabriel.cognia.system.service.MinioStorageService;
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
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final int MAX_TOOL_ROUNDS = 6;
    private static final String HIT_NONE = "NONE";
    private static final String HIT_SEMANTIC = "SEMANTIC";
    private static final String HIT_RETRIEVAL = "RETRIEVAL";

    private final ModelConfigService modelConfigService;
    private final RetrievalService retrievalService;
    private final WebToolService webToolService;
    private final PromptSettingService promptSettingService;
    private final ChatConversationService conversationService;
    private final ChannelService channelService;
    private final MetricEventService metricEventService;
    private final RagSettingService ragSettingService;
    private final bao.gabriel.cognia.system.service.ToolSettingService toolSettingService;
    private final ChatTraceService chatTraceService;
    private final DebugSettingService debugSettingService;
    private final ChatProtocolDispatcher chatProtocolDispatcher;
    private final EmbeddingService embeddingService;
    private final RagCacheService ragCacheService;
    private final ContextWindowManager contextWindowManager;
    private final OpenAiCompatibleClient client;
    private final MinioStorageService storageService;
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
        TraceRecorder trace = null;
        long qaStart = System.nanoTime();
        long metricChannelId = request.channelIds() == null || request.channelIds().isEmpty()
                ? 0L : request.channelIds().get(0);
        Long metricConversationId = null;
        ResolvedModel metricModel = null;
        boolean metricRetrievalUsed = false;
        boolean metricRetrievalHit = false;
        boolean metricRerankUsed = false;
        long metricRetrievalMs = 0L;
        int metricLlmRounds = 0;
        int metricInputTokens = 0;
        int metricOutputTokens = 0;
        boolean metricRefused = false;
        String metricStatus = "SUCCESS";
        String metricErrorType = null;
        try {
            ResolvedModel model = modelConfigService.resolveChat(request.modelId());
            metricModel = model;
            RagSetting setting = ragSettingService.getSetting();
            List<ChatAttachmentRequest> attachments = attachments(request);
            boolean hasImageAttachment = attachments.stream()
                    .anyMatch(item -> "IMAGE".equalsIgnoreCase(item.type()));
            if (!attachments.isEmpty() && !model.supportsVision()) {
                throw bao.gabriel.cognia.common.exception.BusinessException.badRequest("当前模型不支持文件上传，请选择支持视觉的模型");
            }
            String lastUser = lastUserMessage(request);
            metricInputTokens = estimateTokens(lastUser);
            boolean traceEnabled = debugSettingService.shouldTrace(userId);
            trace = chatTraceService.start(userId, request.conversationId(), model, lastUser, traceEnabled);
            if (trace != null) {
                trace.put("meta", "effort", request.effort());
                trace.put("meta", "thinking", model.thinkingEnabled());
                trace.put("meta", "stream", true);
                trace.put("meta", "rag", request.channelIds() != null && !request.channelIds().isEmpty());
                trace.put("meta", "channels", request.channelIds());
                trace.put("meta", "contextWindow", model.contextWindowOrDefault());
                trace.put("meta", "maxOutputTokens", model.maxOutputTokensOrDefault());
                trace.put("meta", "ragEngineVersion", "context-window+cache-v3");
                trace.put("meta", "promptVersion", "prompt-setting-v1");
                trace.put("meta", "vectorVersion", "pgvector-1536");
                trace.put("input", "rawQuery", lastUser);
                trace.put("input", "finalQuery", lastUser);
                trace.put("input", "rewrite", java.util.Map.of("enabled", false, "strategy", "none"));
                trace.put("input", "validation", java.util.Map.of(
                        "attachments", attachments.size(),
                        "length", lastUser == null ? 0 : lastUser.length()));
            }
            boolean temporary = Boolean.TRUE.equals(request.temporary());
            boolean newConversation = !temporary && !StringUtils.hasText(request.conversationId());
            boolean editing = request.editMessageIndex() != null;
            ChatConversation conversation = null;
            String conversationPublicId = "";
            if (!temporary) {
                conversation = conversationService.ensure(request.conversationId(), userId,
                        lastUser, model.id(), request.channelIds());
                conversationPublicId = conversation.getPublicId();
                metricConversationId = conversation.getId();
                attachments = promoteDraftAttachments(attachments, conversationPublicId);
                if (editing) {
                    conversationService.truncateFrom(conversationPublicId, userId, request.editMessageIndex());
                    if (StringUtils.hasText(lastUser)) {
                        conversationService.append(conversationPublicId, userId, "user", lastUser, null, null,
                                attachmentsJson(attachments));
                    }
                } else if (Boolean.TRUE.equals(request.regenerate())) {
                    conversationService.removeLastAssistant(conversationPublicId, userId);
                } else if (StringUtils.hasText(lastUser)) {
                    conversationService.append(conversationPublicId, userId, "user", lastUser, null, null,
                                attachmentsJson(attachments));
                }
            }
            send(emitter, event("start").put("model", model.modelId())
                    .put("provider", model.providerName()).put("supportsTools", model.supportsTools())
                    .put("supportsVision", model.supportsVision())
                    .put("conversationId", conversationPublicId));
            if (trace != null) {
                send(emitter, event("trace").put("traceId", trace.traceId()));
            }

            ragCacheService.recordRequest();
            boolean semanticEnabled = setting.semanticCacheEnabled() && !hasImageAttachment
                    && !Boolean.TRUE.equals(request.regenerate()) && !editing;
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
            String cachedContextJson = "[]";

            if (StringUtils.hasText(lastUser)) {
                if (semanticEnabled) {
                    queryVector = embedOrNull(lastUser);
                    RagCacheService.SemanticHit hit = queryVector == null ? null
                            : ragCacheService.findSemantic(scopeHash, lastUser, queryVector, setting);
                    if (hit != null) {
                        hitType = HIT_SEMANTIC;
                        similarity = hit.similarity();
                        cachedAnswer = hit.answer();
                        cachedContextJson = hit.contextJson();
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
                        cachedContextJson = hit.contextJson();
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

            if (trace != null) {
                trace.put("cache", "semanticEnabled", semanticEnabled);
                trace.put("cache", "retrievalEnabled", retrievalEnabled);
                trace.put("cache", "hitType", hitType);
                trace.put("cache", "similarity", similarity);
                trace.put("cache", "cacheKey", cacheKey);
                trace.put("cache", "channels", request.channelIds());
            }
            if (HIT_SEMANTIC.equals(hitType) && cachedAnswer != null) {
                ragCacheService.recordHit(HIT_SEMANTIC);
                if (trace != null) {
                    trace.put("output", "answer", cachedAnswer);
                    trace.put("output", "source", "SEMANTIC_CACHE");
                    trace.stage("done", System.nanoTime(), "SUCCESS");
                    chatTraceService.finish(trace, "SUCCESS", cachedAnswer);
                }
                send(emitter, cacheEvent(hitType, similarity, cacheKey));
                List<CitationSource> cachedCitations = usedCitations(
                        citationsFromJson(cachedContextJson), cachedAnswer);
                send(emitter, citationsEvent(cachedCitations));
                // Cached answers do not build a prompt, but the client still
                // expects a context event for its usage ring.
                send(emitter, contextEvent(emptyContext(model, setting)));
                Long cachedMessageId = null;
                if (conversation != null) {
                    cachedMessageId = conversationService.append(conversationPublicId, userId, "assistant",
                            cachedAnswer, "", citationsJson(cachedCitations), null);
                    if (newConversation) {
                        conversationService.generateTitleAsync(conversationPublicId, model.id(), lastUser, cachedAnswer);
                    }
                }
                metricOutputTokens = estimateTokens(cachedAnswer);
                metricRefused = isRefusal(cachedAnswer);
                send(emitter, event("text").put("delta", cachedAnswer));
                send(emitter, doneEvent(hitType, similarity, cacheKey, cachedMessageId));
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
            long retrievalStart = System.nanoTime();
            boolean embeddingMeasured = queryVector == null && hasChannels && StringUtils.hasText(lastUser);
            if (chunks == null) {
                if (hasChannels && StringUtils.hasText(lastUser)) {
                    metricRetrievalUsed = true;
                    chunks = queryVector != null
                            ? retrievalService.retrieve(lastUser, queryVector, userId, request.channelIds(),
                                    request.debugTopK(), request.debugRerankEnabled())
                            : retrievalService.retrieve(lastUser, userId, request.channelIds());
                    metricRetrievalHit = chunks != null && !chunks.isEmpty();
                    metricRerankUsed = setting.rerankEnabled() && setting.getRerankModelId() != null;
                    metricRetrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000;
                    metricInputTokens = estimateTokens(lastUser);
                    if (chunks != null) {
                        for (RetrievedChunk chunk : chunks) {
                            metricInputTokens += estimateTokens(chunk.chunkText());
                        }
                    }
                    if (retrievalEnabled) {
                        ragCacheService.storeRetrievalAsync(scopeHash, lastUser, chunks, setting);
                    }
                } else {
                    chunks = List.of();
                }
            }
            if (trace != null) {
                trace.stage("retrieval", retrievalStart, "SUCCESS");
                traceRetrieval(trace, setting, chunks, hitType, queryVector, request.channelIds());
            }

            List<CitationSource> sources = citations(chunks);
            List<CitationSource> allSources = new ArrayList<>(sources);
            send(emitter, citationsEvent(allSources));
            long promptStart = System.nanoTime();
            ArrayNode messages = buildMessages(request, userId, chunks, conversationPublicId);
            bao.gabriel.cognia.system.entity.PromptSetting promptSetting = promptSettingService.getSetting();
            ContextWindowManager.Prepared prepared = contextWindowManager.prepare(messages, model,
                    setting.contextCompressionThreshold(), promptSetting.getCompressionPrompt());
            send(emitter, contextEvent(prepared));
            if (trace != null) {
                trace.stage("prompt", promptStart, "SUCCESS");
                tracePrompt(trace, messages, prepared, promptSetting, model);
            }

            String effort = model.thinkingEnabled() ? request.effort() : null;
            boolean toolsAvailable = model.supportsTools() && toolsUsable(setting, request);
            StringBuilder answer = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            java.util.Set<String> seenToolCalls = new java.util.HashSet<>();
            boolean truncated = false;
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                boolean includeTools = toolsAvailable && round < MAX_TOOL_ROUNDS;
                if (round == MAX_TOOL_ROUNDS && toolsAvailable) {
                    ObjectNode finalInstruction = messages.addObject();
                    finalInstruction.put("role", "system");
                    finalInstruction.put("content",
                            "工具调用阶段已结束。请停止调用工具，基于以上检索结果直接给出简洁、完整的最终回答。");
                }
                long llmStart = System.nanoTime();
                metricLlmRounds++;
                StreamOutcome outcome = streamCompletion(model, messages, effort, emitter, includeTools, trace);
                if (trace != null) {
                    trace.stage("llm.round" + round, llmStart, "SUCCESS");
                }
                reasoning.append(outcome.reasoning());
                if (outcome.toolCalls().isEmpty()) {
                    answer.append(outcome.content());
                    break;
                }
                if (round == MAX_TOOL_ROUNDS) {
                    truncated = true;
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
                    if (!seenToolCalls.add(name + ":" + arguments)) {
                        truncated = true;
                        break;
                    }
                    send(emitter, event("tool").put("id", id).put("name", name).put("status", "running")
                            .put("arguments", arguments));
                    long toolStart = System.nanoTime();
                    String output;
                    String toolError = null;
                    try {
                        output = webToolService.execute(name, arguments, model.apiKey());
                    } catch (Exception ex) {
                        toolError = ex.getMessage();
                        output = "工具执行失败：" + ex.getMessage();
                    }
                    if (trace != null) {
                        traceTool(trace, round, name, id, arguments, output, toolError,
                                (System.nanoTime() - toolStart) / 1_000_000);
                    }
                    List<CitationSource> webSources = webCitations(name, output, allSources.size() + 1);
                    if (!webSources.isEmpty()) {
                        allSources.addAll(webSources);
                        send(emitter, citationsEvent(allSources));
                        output = "Web search results. If you use one, cite it with its bracketed index:\n"
                                + webSourceLabels(webSources) + "\n\n" + output;
                    }
                    ObjectNode toolMessage = messages.addObject();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", id);
                    toolMessage.put("content", truncate(output, 12000));
                    send(emitter, event("tool").put("id", id).put("name", name).put("status", "done")
                            .put("output", truncate(output, 6000)));
                }
                if (truncated) {
                    break;
                }
            }
            if (truncated) {
                metricStatus = "TRUNCATED";
                if (trace != null) {
                    trace.put("output", "answer", answer.toString());
                    trace.stage("done", System.nanoTime(), "TRUNCATED");
                    chatTraceService.finish(trace, "TRUNCATED", answer.toString());
                }
                send(emitter, event("truncated").put("message", "生成已停止，请重新尝试")
                        .put("reason", "模型连续重复工具调用或超过最大工具轮次"));
                emitter.complete();
                return;
            }
            allSources = usedCitations(allSources, answer.toString());
            send(emitter, citationsEvent(allSources));
            Long assistantMessageId = null;
            if (conversation != null) {
                assistantMessageId = conversationService.append(conversation.getPublicId(), userId, "assistant",
                        answer.toString(), reasoning.toString(), citationsJson(allSources), null);
                if (newConversation || (editing && request.editMessageIndex() != null
                        && request.editMessageIndex() == 0)) {
                    conversationService.generateTitleAsync(conversation.getPublicId(), model.id(),
                            lastUser, answer.toString());
                }
            }
            // Best-effort write; never blocks or fails the streamed response.
            if (semanticEnabled && queryVector != null && !answer.isEmpty()
                    && StringUtils.hasText(lastUser)) {
                try {
                    ragCacheService.storeSemanticAsync(scopeHash, lastUser, answer.toString(),
                            citationsContextJson(allSources), queryVector, setting);
                } catch (Exception ex) {
                    log.warn("Semantic cache write skipped: {}", ex.getMessage());
                }
            }
            if (trace != null) {
                trace.put("output", "answer", answer.toString());
                trace.put("output", "citations", allSources.size());
                trace.put("output", "citationCheck", citationCheck(allSources, answer.toString()));
                trace.put("output", "source", "LLM");
                trace.stage("postprocess", System.nanoTime(), "SUCCESS");
                chatTraceService.finish(trace, "SUCCESS", answer.toString());
            }
            metricOutputTokens = estimateTokens(answer.toString());
            metricRefused = isRefusal(answer.toString());
            send(emitter, doneEvent(hitType, similarity, cacheKey, assistantMessageId));
            emitter.complete();
        } catch (Exception ex) {
            metricStatus = "FAILED";
            metricErrorType = classifyError(ex);
            log.warn("Chat request failed", ex);
            if (trace != null) {
                trace.error("chat", safeMessage(ex));
                chatTraceService.finish(trace, "FAILED", null);
            }
            try {
                send(emitter, event("error").put("message", safeMessage(ex)));
            } catch (Exception ignored) {
                // stream already gone
            }
            emitter.completeWithError(ex);
        } finally {
            metricEventService.recordQa(userId, metricChannelId, metricConversationId,
                    metricModel == null ? null : metricModel.id(),
                    metricModel == null ? null : metricModel.modelId(),
                    metricStatus, metricErrorType,
                    (System.nanoTime() - qaStart) / 1_000_000,
                    metricRetrievalUsed, metricRetrievalHit, metricRerankUsed,
                    0, metricLlmRounds, metricRefused,
                    metricInputTokens, metricOutputTokens,
                    metricRetrievalUsed ? estimateTokens(lastUserMessage(request)) : 0,
                    metricRetrievalMs, 0);
            if (stampedeLock != null && stampedeLock.isHeldByCurrentThread()) {
                stampedeLock.unlock();
            }
        }
    }

    private static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }

    private static boolean isRefusal(String text) {
        if (text == null || text.isBlank()) return false;
        return text.contains("无法回答") || text.contains("抱歉") || text.contains("没有找到相关")
                || text.contains("无法确定") || text.contains("我不知道");
    }

    private static String classifyError(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) return "TIMEOUT";
        if (message.contains("429") || message.contains("rate limit")) return "RATE_LIMIT";
        if (message.contains("401") || message.contains("403") || message.contains("unauthorized")
                || message.contains("forbidden")) return "AUTH";
        return "ERROR";
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

    private ObjectNode citationsEvent(List<CitationSource> sources) {
        ObjectNode payload = event("citations");
        ArrayNode array = payload.putArray("sources");
        for (CitationSource source : sources) {
            ObjectNode node = array.addObject();
            node.put("index", source.index());
            node.put("documentId", source.documentId());
            node.put("channelId", source.channelId());
            node.put("channelName", source.channelName());
            node.put("channelSlug", source.channelSlug());
            node.put("ownerType", source.ownerType());
            node.put("ownerId", source.ownerId());
            node.put("fileName", source.fileName());
            node.put("title", source.title());
            node.put("documentType", source.documentType());
            node.put("pageNumber", source.pageNumber());
            node.put("chunkIndex", source.chunkIndex());
            node.put("snippet", source.snippet());
            node.put("score", source.score());
            node.put("url", source.url());
            node.put("kind", source.kind());
        }
        return payload;
    }

    private List<CitationSource> citations(List<RetrievedChunk> chunks) {
        List<CitationSource> sources = new ArrayList<>();
        int index = 1;
        for (RetrievedChunk chunk : chunks) {
            sources.add(new CitationSource(index++, chunk.documentId(), chunk.channelId(),
                    chunk.channelName(), chunk.channelSlug(), chunk.ownerType(), chunk.ownerId(),
                    chunk.fileName(), chunk.title(), chunk.documentType(), chunk.pageNumber(),
                    chunk.chunkIndex(), truncate(chunk.chunkText(), 1600), chunk.score()));
        }
        return sources;
    }

    private List<CitationSource> citationsFromJson(String contextJson) {
        List<CitationSource> sources = new ArrayList<>();
        if (!StringUtils.hasText(contextJson)) {
            return sources;
        }
        try {
            JsonNode array = mapper.readTree(contextJson);
            if (!array.isArray()) {
                return sources;
            }
            int index = 1;
            for (JsonNode node : array) {
                sources.add(new CitationSource(index++,
                        node.path("documentId").isNumber() ? node.path("documentId").asLong() : null,
                        node.path("channelId").isNumber() ? node.path("channelId").asLong() : null,
                        textOrNull(node, "channelName"), textOrNull(node, "channelSlug"),
                        textOrNull(node, "ownerType"),
                        node.path("ownerId").isNumber() ? node.path("ownerId").asLong() : null,
                        textOrNull(node, "fileName"), textOrNull(node, "title"),
                        textOrNull(node, "documentType"),
                        node.path("pageNumber").isNumber() ? node.path("pageNumber").asInt() : null,
                        node.path("chunkIndex").isNumber() ? node.path("chunkIndex").asInt() : null,
                        textOrNull(node, "snippet") != null ? textOrNull(node, "snippet") : textOrNull(node, "text"),
                        0.0, textOrNull(node, "url"),
                        textOrNull(node, "kind") != null ? textOrNull(node, "kind") : "DOCUMENT"));
            }
        } catch (Exception ex) {
            log.debug("Unable to parse citation context: {}", ex.getMessage());
        }
        return sources;
    }

    private List<CitationSource> webCitations(String tool, String output, int startIndex) {
        List<CitationSource> result = new ArrayList<>();
        if (!StringUtils.hasText(output) || output.startsWith("工具执行失败") || output.startsWith("搜索失败")
                || output.startsWith("抓取失败") || output.startsWith("搜索关键词为空")) {
            return result;
        }
        java.util.LinkedHashMap<String, CitationSource> unique = new java.util.LinkedHashMap<>();
        int index = startIndex;
        if ("web_fetch".equals(tool)) {
            java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("(?m)^URL:\\s*(https?://\\S+)")
                    .matcher(output);
            if (urlMatcher.find()) {
                String url = urlMatcher.group(1);
                String title = output.lines().filter(line -> line.startsWith("# ")).findFirst()
                        .map(line -> line.substring(2).trim()).orElse(hostOf(url));
                unique.put(url, new CitationSource(index++, null, null, null, null, null, null,
                        hostOf(url), title, "web", null, null, truncate(output, 300), 0.0, url, "WEB"));
            }
        } else {
            try {
                JsonNode root = mapper.readTree(output);
                JsonNode array = root;
                if (root.isObject()) {
                    for (String field : new String[]{"results", "data", "items", "webPages", "organic_results"}) {
                        if (root.path(field).isArray()) {
                            array = root.path(field);
                            break;
                        }
                    }
                }
                if (array.isArray()) {
                    for (JsonNode item : array) {
                        String url = item.path("url").asText(item.path("link").asText(""));
                        if (!StringUtils.hasText(url) || !url.startsWith("http")) {
                            continue;
                        }
                        String title = item.path("title").asText(item.path("name").asText(hostOf(url)));
                        String snippet = item.path("snippet").asText(item.path("content").asText(
                                item.path("description").asText("")));
                        unique.putIfAbsent(url, new CitationSource(index + unique.size(), null, null, null, null,
                                null, null, hostOf(url), title, "web", null, null, truncate(snippet, 300),
                                0.0, url, "WEB"));
                    }
                }
            } catch (Exception ignored) {
                java.util.regex.Matcher text = java.util.regex.Pattern
                        .compile("(?m)^-\\s+(.+)\\n\\s+(https?://\\S+)\\s*$").matcher(output);
                while (text.find() && unique.size() < 10) {
                    String title = text.group(1).trim();
                    String url = text.group(2).trim();
                    unique.putIfAbsent(url, new CitationSource(index + unique.size(), null, null, null, null,
                            null, null, hostOf(url), title, "web", null, null, "", 0.0, url, "WEB"));
                }
            }
        }
        result.addAll(unique.values());
        return result;
    }

    private String webSourceLabels(List<CitationSource> sources) {
        StringBuilder builder = new StringBuilder();
        for (CitationSource source : sources) {
            builder.append("[").append(source.index()).append("] ").append(source.title())
                    .append(" - ").append(source.url()).append("\n");
        }
        return builder.toString();
    }

    private String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception ex) {
            return "web";
        }
    }

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,3})\\]");

    private List<CitationSource> usedCitations(List<CitationSource> sources, String text) {
        if (sources == null || sources.isEmpty() || !StringUtils.hasText(text)) {
            return List.of();
        }
        Set<Integer> used = new HashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(text);
        while (matcher.find()) {
            used.add(Integer.parseInt(matcher.group(1)));
        }
        if (used.isEmpty()) {
            return List.of();
        }
        return sources.stream().filter(source -> used.contains(source.index())).toList();
    }

    private String attachmentsJson(java.util.List<bao.gabriel.cognia.system.dto.chat.ChatAttachmentRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        com.fasterxml.jackson.databind.node.ArrayNode array = mapper.createArrayNode();
        for (var attachment : attachments) {
            com.fasterxml.jackson.databind.node.ObjectNode node = array.addObject();
            node.put("type", attachment.type());
            node.put("name", attachment.name());
            node.put("mimeType", attachment.mimeType());
            node.put("text", attachment.text());
            node.put("key", attachment.key());
        }
        return array.toString();
    }

    private String citationsJson(List<CitationSource> sources) {
        try {
            return mapper.writeValueAsString(sources);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private ObjectNode doneEvent(String hitType, double similarity, String cacheKey, Long assistantMessageId) {
        ObjectNode done = event("done")
                .put("hitType", hitType)
                .put("similarity", round(similarity))
                .put("cacheKey", cacheKey);
        if (assistantMessageId != null) {
            done.put("assistantMessageId", String.valueOf(assistantMessageId));
        }
        return done;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String citationsContextJson(List<CitationSource> sources) {
        ArrayNode context = mapper.createArrayNode();
        for (CitationSource source : sources) {
            ObjectNode node = context.addObject();
            node.put("index", source.index());
            node.put("documentId", source.documentId());
            node.put("channelId", source.channelId());
            node.put("channelName", source.channelName());
            node.put("channelSlug", source.channelSlug());
            node.put("ownerType", source.ownerType());
            node.put("ownerId", source.ownerId());
            node.put("fileName", source.fileName());
            node.put("title", source.title());
            node.put("documentType", source.documentType());
            node.put("pageNumber", source.pageNumber());
            node.put("chunkIndex", source.chunkIndex());
            node.put("snippet", source.snippet());
            node.put("score", source.score());
            node.put("url", source.url());
            node.put("kind", source.kind());
        }
        return context.toString();
    }

    private String contextJson(List<RetrievedChunk> chunks) {
        ArrayNode context = mapper.createArrayNode();
        for (RetrievedChunk chunk : chunks) {
            ObjectNode node = context.addObject();
            node.put("documentId", chunk.documentId());
            node.put("channelId", chunk.channelId());
            node.put("channelName", chunk.channelName());
            node.put("channelSlug", chunk.channelSlug());
            node.put("ownerType", chunk.ownerType());
            node.put("ownerId", chunk.ownerId());
            node.put("fileName", chunk.fileName());
            node.put("title", chunk.title());
            node.put("documentType", chunk.documentType());
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
    /**
     * Tools are only offered when at least one of them can actually run: the
     * search endpoint is configured (no implicit fallback) or web_fetch is on.
     */
    private boolean toolsUsable(RagSetting setting, ChatRequest request) {
        if (Boolean.FALSE.equals(request.debugToolsEnabled())) {
            return false;
        }
        return (webSearchAllowed(setting, request) && toolSettingService.webSearchUsable())
                || toolSettingService.webFetchEnabled();
    }

    private boolean webSearchAllowed(RagSetting setting, ChatRequest request) {
        if (!setting.webSearchEnabled() || !toolSettingService.webSearchUsable()) {
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

    private List<ChatAttachmentRequest> promoteDraftAttachments(List<ChatAttachmentRequest> attachments, String conversationId) {
        if (attachments == null || attachments.isEmpty() || !StringUtils.hasText(conversationId)) {
            return attachments;
        }
        List<ChatAttachmentRequest> promoted = new ArrayList<>(attachments.size());
        for (ChatAttachmentRequest item : attachments) {
            String key = item.key();
            if (key == null || !key.startsWith("conv/") || !key.contains("/draft/")) {
                promoted.add(item);
                continue;
            }
            String target = key.replaceFirst("/draft/", "/" + conversationId + "/");
            try {
                storageService.copyChatImage(key, target);
                promoted.add(new ChatAttachmentRequest(item.type(), item.name(), item.mimeType(),
                        item.dataUrl(), item.text(), item.url(), target));
            } catch (RuntimeException ex) {
                log.warn("Failed to promote draft attachment {}: {}", key, ex.getMessage());
                promoted.add(item);
            }
        }
        return promoted;
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

    private ArrayNode buildMessages(ChatRequest request, Long userId, List<RetrievedChunk> chunks,
                                    String conversationPublicId) {
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
            system.append("""
                    \nCitation rules:
                    - When a statement uses the retrieved knowledge above, add a marker like [1] or [2] at the end of that sentence.
                    - The number must match the retrieved source number shown above.
                    - Only cite sources you actually used. Never invent numbers or sources.
                    - Do not add a separate references/bibliography section; the UI renders the source list.
                    """);
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
        List<ConversationMessageResponse> persisted = List.of();
        if (StringUtils.hasText(conversationPublicId)) {
            try {
                persisted = conversationService.detail(conversationPublicId, userId).messages();
            } catch (Exception ex) {
                log.debug("Failed to load persisted messages for image context: {}", ex.getMessage());
            }
        }
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
            String role = normalizeRole(message.role());
            List<String> messageImages = new ArrayList<>();
            if (lastUser) {
                messageImages.addAll(imageUrls);
            } else if ("user".equals(role) && i < persisted.size()) {
                ConversationMessageResponse stored = persisted.get(i);
                if (stored.attachments() != null) {
                    for (ChatAttachmentRequest attachment : stored.attachments()) {
                        if ("IMAGE".equalsIgnoreCase(attachment.type())) {
                            String inline = inlineImage(attachment);
                            if (StringUtils.hasText(inline)) {
                                messageImages.add(inline);
                            }
                        }
                    }
                }
            }
            if (content.isBlank() && messageImages.isEmpty()) {
                continue;
            }
            ObjectNode node = messages.addObject();
            node.put("role", role);
            if ("user".equals(role) && !messageImages.isEmpty()) {
                ArrayNode parts = node.putArray("content");
                parts.addObject().put("type", "text").put("text", content.isBlank() ? "图片消息" : content);
                for (String url : messageImages) {
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

    private String inlineImage(ChatAttachmentRequest attachment) {
        if (StringUtils.hasText(attachment.key())) {
            try {
                byte[] bytes = storageService.readObject(attachment.key());
                if (bytes != null && bytes.length > 0) {
                    String mime = StringUtils.hasText(attachment.mimeType()) ? attachment.mimeType() : "image/png";
                    return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
                }
            } catch (Exception ex) {
                log.debug("Failed to inline historical image {}: {}", attachment.key(), ex.getMessage());
            }
        }
        return attachment.url();
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

    /**
     * Streams one model round through the protocol dispatcher and applies the
     * DSML sanitizer to both reasoning and visible text. Any provider protocol
     * (Chat Completions, Responses, Anthropic Messages) is handled by its own
     * client; this method only owns sanitising, SSE forwarding and tracing.
     */
    private StreamOutcome streamCompletion(ResolvedModel model, ArrayNode messages, String effort,
                                           SseEmitter emitter, boolean includeTools, TraceRecorder trace) throws Exception {
        ArrayNode tools = mapper.createArrayNode();
        if (includeTools) {
            tools = webToolService.toolDefinitions();
            includeTools = tools.size() > 0;
        }
        StringBuilder fullText = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        StreamingTextSanitizer textSanitizer = new StreamingTextSanitizer();
        StreamingTextSanitizer reasoningSanitizer = new StreamingTextSanitizer();
        if (trace != null) {
            trace.put("llm", "protocol", model.protocol());
            trace.put("llm", "endpoint", model.baseUrl());
            trace.put("llm", "skipReasoning", false);
        }
        final boolean withTools = includeTools;
        ChatProtocolClient.Result result = chatProtocolDispatcher.stream(model, messages, effort, withTools, tools,
                new ChatProtocolClient.Listener() {
                    @Override
                    public void onReasoning(String delta) throws Exception {
                        String safe = reasoningSanitizer.accept(delta);
                        if (!safe.isEmpty()) {
                            fullReasoning.append(safe);
                            if (trace != null) {
                                trace.firstToken();
                                trace.rawChunk("reasoning", safe);
                            }
                            send(emitter, event("reasoning").put("delta", safe));
                        }
                    }

                    @Override
                    public void onText(String delta) throws Exception {
                        String safe = textSanitizer.accept(delta);
                        if (!safe.isEmpty()) {
                            fullText.append(safe);
                            if (trace != null) {
                                trace.firstToken();
                                trace.rawChunk("text", safe);
                            }
                            send(emitter, event("text").put("delta", safe));
                        }
                    }

                    @Override
                    public void onRawChunk(String json) throws Exception {
                        if (trace != null) {
                            trace.rawPayload(json);
                        }
                    }

                    @Override
                    public void onRequest(ObjectNode body) throws Exception {
                        if (trace != null) {
                            trace.set("llm", "request", body);
                        }
                    }
                });
        String textTail = textSanitizer.flush();
        if (!textTail.isEmpty()) {
            fullText.append(textTail);
            send(emitter, event("text").put("delta", textTail));
        }
        String reasoningTail = reasoningSanitizer.flush();
        if (!reasoningTail.isEmpty()) {
            fullReasoning.append(reasoningTail);
            send(emitter, event("reasoning").put("delta", reasoningTail));
        }
        ArrayNode calls = result == null || result.toolCalls() == null ? mapper.createArrayNode() : result.toolCalls();
        log.debug("Chat stream completed with {} chars, {} tool calls", fullText.length(), calls.size());
        return new StreamOutcome(fullText.toString(), fullReasoning.toString(), calls);
    }

    /** Records retrieval parameters, candidates and the assembled context. */
    private void traceRetrieval(TraceRecorder trace, RagSetting setting, List<RetrievedChunk> chunks,
                                String hitType, float[] queryVector, List<Long> channelIds) {
        trace.put("retrieval", "strategy", "HYBRID_VECTOR");
        trace.put("retrieval", "hitType", hitType);
        trace.put("retrieval", "topK", setting.retrievalTopK());
        trace.put("retrieval", "topN", setting.retrievalTopN());
        trace.put("retrieval", "rerankEnabled", setting.rerankEnabled());
        trace.put("retrieval", "channels", channelIds);
        trace.put("retrieval", "vectorDimension", queryVector == null ? null : queryVector.length);
        ArrayNode candidates = mapper.createArrayNode();
        for (RetrievedChunk chunk : chunks) {
            ObjectNode item = candidates.addObject();
            item.put("chunkId", chunk.id());
            item.put("documentId", chunk.documentId());
            item.put("channelId", chunk.channelId());
            item.put("channelName", chunk.channelName());
            item.put("fileName", chunk.fileName());
            item.put("title", chunk.title());
            item.put("chunkIndex", chunk.chunkIndex());
            item.put("pageNumber", chunk.pageNumber());
            item.put("score", round(chunk.score()));
            item.put("expired", chunk.expired());
            item.put("embeddingModel", chunk.embeddingModel());
            item.put("text", truncate(chunk.chunkText(), 1200));
        }
        trace.set("retrieval", "candidates", candidates);
        trace.put("retrieval", "resultCount", chunks.size());
    }

    /** Records the prompt template, the final messages and token accounting. */
    private void tracePrompt(TraceRecorder trace, ArrayNode messages, ContextWindowManager.Prepared prepared,
                             bao.gabriel.cognia.system.entity.PromptSetting promptSetting, ResolvedModel model) {
        trace.put("prompt", "systemPrompt", promptSetting.getSystemPrompt());
        trace.put("prompt", "compressionPrompt", promptSetting.getCompressionPrompt());
        trace.set("prompt", "messages", messages);
        int systemTokens = 0;
        int historyTokens = 0;
        int userTokens = 0;
        for (JsonNode message : messages) {
            int tokens = bao.gabriel.cognia.common.util.TokenEstimator.estimate(
                    message.path("content").isArray() ? message.path("content").toString()
                            : message.path("content").asText(""));
            String role = message.path("role").asText("");
            if ("system".equals(role)) {
                systemTokens += tokens;
            } else if ("user".equals(role)) {
                userTokens += tokens;
            } else {
                historyTokens += tokens;
            }
        }
        ObjectNode stats = trace.section("prompt").putObject("tokenStats");
        stats.put("system", systemTokens);
        stats.put("history", historyTokens);
        stats.put("user", userTokens);
        stats.put("reservedOutput", model.maxOutputTokensOrDefault());
        stats.put("used", prepared.usedTokens());
        stats.put("max", prepared.maxTokens());
        ObjectNode window = trace.section("prompt").putObject("window");
        window.put("compressed", prepared.compressed());
        window.put("droppedMessages", prepared.droppedMessages());
        window.put("threshold", Math.round(prepared.compressionThreshold() * 100));
    }

    private void traceTool(TraceRecorder trace, int round, String name, String id, String arguments,
                           String output, String error, long durationMs) {
        ObjectNode tools = trace.section("tools");
        ArrayNode calls = tools.get("calls") instanceof ArrayNode array ? array : tools.putArray("calls");
        ObjectNode call = calls.addObject();
        call.put("round", round);
        call.put("id", id);
        call.put("name", name);
        call.put("arguments", truncate(arguments, 2000));
        boolean valid = true;
        try {
            mapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        } catch (Exception ex) {
            valid = false;
        }
        call.put("argumentsValid", valid);
        call.put("durationMs", durationMs);
        call.put("output", truncate(output, 4000));
        call.put("error", error);
        tools.put("count", calls.size());
    }

    /** Checks that every citation marker in the answer exists in the sources. */
    private ObjectNode citationCheck(List<CitationSource> sources, String answer) {
        ObjectNode check = mapper.createObjectNode();
        java.util.Set<Integer> available = new java.util.HashSet<>();
        for (CitationSource source : sources) {
            available.add(source.index());
        }
        java.util.Set<Integer> used = new java.util.TreeSet<>();
        java.util.Set<Integer> unknown = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[(\\d{1,3})]").matcher(answer);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (available.contains(index)) {
                used.add(index);
            } else {
                unknown.add(index);
            }
        }
        check.put("available", available.size());
        check.set("used", mapper.valueToTree(used));
        check.set("unknown", mapper.valueToTree(unknown));
        check.put("valid", unknown.isEmpty());
        return check;
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
