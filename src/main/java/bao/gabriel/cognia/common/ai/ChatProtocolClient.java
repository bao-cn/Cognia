package bao.gabriel.cognia.common.ai;

import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Transport abstraction for the streaming chat protocols supported by the
 * project. Implementations receive the conversation in the OpenAI Chat
 * Completions message shape and are responsible for translating it to their
 * own wire format and back.
 *
 * <p>Keeping a single input shape lets the chat service build one message
 * history and switch protocol (or provider) without rewriting the prompt.</p>
 */
public interface ChatProtocolClient {

    /** One of {@code OPENAI_CHAT}, {@code OPENAI_RESPONSE}, {@code ANTHROPIC_MESSAGES}. */
    String protocol();

    Result stream(Request request, Listener listener);

    /**
     * @param model        fully resolved model/provider configuration
     * @param messages     OpenAI Chat Completions message array
     * @param effort       optional reasoning label chosen for this turn
     * @param includeTools whether tool definitions should be sent
     * @param tools        OpenAI function-tool definitions
     * @param extraBodyJson optional JSON object merged into the request body
     */
    record Request(ResolvedModel model, ArrayNode messages, String effort, boolean includeTools,
                   ArrayNode tools, String extraBodyJson) {
    }

    /**
     * Streaming callbacks. Implementations must invoke them in arrival order;
     * exceptions are propagated and abort the stream.
     */
    interface Listener {
        void onReasoning(String delta) throws Exception;

        void onText(String delta) throws Exception;

        /** One raw SSE data payload, for the debug trace. */
        void onRawChunk(String json) throws Exception;

        /** The exact request body sent to the provider, for the debug trace. */
        default void onRequest(com.fasterxml.jackson.databind.node.ObjectNode body) throws Exception {
        }
    }

    /**
     * @param content   concatenated assistant text
     * @param reasoning concatenated reasoning/thinking text
     * @param toolCalls assembled calls in OpenAI shape, ready to append to the
     *                  assistant message
     */
    record Result(String content, String reasoning, ArrayNode toolCalls,
                  Integer promptTokens, Integer completionTokens,
                  Integer promptCacheHitTokens, Integer promptCacheMissTokens) {

        public Result(String content, String reasoning, ArrayNode toolCalls) {
            this(content, reasoning, toolCalls, null, null, null, null);
        }
    }
}
