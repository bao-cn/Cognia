package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.util.TokenEstimator;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Budgets the chat prompt against the model context window.
 *
 * <p>When the estimated prompt size reaches the configured threshold the older
 * part of the conversation is summarized by the chat model itself (one
 * non-streaming call) and replaced with a single system message. Afterwards the
 * prompt is trimmed oldest-first until it fits the window. The system prompt and
 * the latest user question are never dropped.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextWindowManager {

    static final double OUTPUT_RESERVE_RATIO = 0.15;
    static final int MIN_INPUT_BUDGET = 512;
    static final String SUMMARY_PREFIX = "Conversation summary:";
    private static final int KEEP_RECENT_MESSAGES = 4;
    private static final int MAX_TRANSCRIPT_CHARS = 30000;

    private final OpenAiCompatibleClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public record Prepared(
            ArrayNode messages,
            int usedTokens,
            int maxTokens,
            double compressionThreshold,
            boolean compressed,
            int droppedMessages
    ) {
    }

    /**
     * Compresses (if needed) and trims {@code messages} in place.
     */
    public Prepared prepare(ArrayNode messages, ResolvedModel model, RagSetting setting) {
        int maxTokens = model.contextWindowOrDefault();
        int inputBudget = Math.max(MIN_INPUT_BUDGET,
                (int) Math.floor(maxTokens * (1.0 - OUTPUT_RESERVE_RATIO)));
        double threshold = Math.min(100, Math.max(1, setting.contextCompressionThreshold())) / 100.0;

        int used = TokenEstimator.estimateMessages(messages);
        boolean compressed = false;
        int dropped = 0;

        // Note: threshold is relative to the full window, budget to the input share.
        if (used >= (int) Math.ceil(maxTokens * threshold) && messages.size() > 1) {
            CompressionResult result = compress(messages, model, setting.compressionPrompt());
            if (result.summary() != null) {
                compressed = true;
                dropped += result.dropped();
                used = TokenEstimator.estimateMessages(messages);
            }
        }
        if (used > inputBudget) {
            dropped += trim(messages, inputBudget);
            used = TokenEstimator.estimateMessages(messages);
        }
        return new Prepared(messages, used, maxTokens, threshold, compressed, dropped);
    }

    private record CompressionResult(String summary, int dropped) {
    }

    private CompressionResult compress(ArrayNode messages, ResolvedModel model, String prompt) {
        int size = messages.size();
        int keepFrom = Math.max(1, size - KEEP_RECENT_MESSAGES);
        if (keepFrom <= 1) {
            return new CompressionResult(null, 0);
        }
        StringBuilder transcript = new StringBuilder();
        for (int i = 1; i < keepFrom; i++) {
            JsonNode message = messages.get(i);
            String role = message.path("role").asText("user");
            String content = message.path("content").asText("");
            if (!StringUtils.hasText(content)) {
                continue;
            }
            transcript.append(role).append(": ").append(truncate(content, 4000)).append('\n');
        }
        if (transcript.isEmpty()) {
            return new CompressionResult(null, 0);
        }
        String text = transcript.length() > MAX_TRANSCRIPT_CHARS
                ? transcript.substring(transcript.length() - MAX_TRANSCRIPT_CHARS)
                : transcript.toString();
        try {
            String summary = summarize(model, prompt, text);
            if (!StringUtils.hasText(summary)) {
                return new CompressionResult(null, 0);
            }
            for (int i = keepFrom - 1; i >= 1; i--) {
                messages.remove(i);
            }
            ObjectNode summaryMessage = mapper.createObjectNode();
            summaryMessage.put("role", "system");
            summaryMessage.put("content", SUMMARY_PREFIX + "\n" + summary.trim());
            messages.insert(1, summaryMessage);
            return new CompressionResult(summary, keepFrom - 1);
        } catch (Exception ex) {
            log.warn("Context compression failed, falling back to sliding window: {}", ex.getMessage());
            return new CompressionResult(null, 0);
        }
    }

    private String summarize(ResolvedModel model, String prompt, String transcript) throws Exception {
        ObjectNode body = client.mapper().createObjectNode();
        body.put("model", model.modelId());
        ArrayNode chatMessages = body.putArray("messages");
        chatMessages.addObject().put("role", "system").put("content", prompt);
        chatMessages.addObject().put("role", "user").put("content", "历史对话记录：\n\n" + transcript);
        body.put("stream", false);
        body.put("temperature", 0.3);

        JsonNode response = client.postJson(model.baseUrl(), "/chat/completions", model.apiKey(), body,
                Duration.ofSeconds(90));
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        return choices.path(0).path("message").path("content").asText("");
    }

    /**
     * Drops the oldest conversation messages until the prompt fits the budget.
     * Keeps the leading system message and the latest user question. Returns the
     * number of dropped messages.
     */
    public int trim(ArrayNode messages, int budget) {
        int dropped = 0;
        int latestUser = -1;
        for (int i = messages.size() - 1; i >= 1; i--) {
            if ("user".equals(messages.get(i).path("role").asText())) {
                latestUser = i;
                break;
            }
        }
        boolean hasSummary = messages.size() > 1 && isSummary(messages.get(1));
        int start = hasSummary ? 2 : 1;

        while (TokenEstimator.estimateMessages(messages) > budget) {
            int victim = -1;
            for (int i = start; i < messages.size(); i++) {
                if (i == latestUser) {
                    continue;
                }
                if (isSummary(messages.get(i))) {
                    continue;
                }
                victim = i;
                break;
            }
            if (victim < 0) {
                break;
            }
            messages.remove(victim);
            dropped++;
            if (victim < latestUser) {
                latestUser--;
            }
        }
        return dropped;
    }

    private static boolean isSummary(JsonNode message) {
        return message != null
                && "system".equals(message.path("role").asText())
                && message.path("content").asText("").startsWith(SUMMARY_PREFIX);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
