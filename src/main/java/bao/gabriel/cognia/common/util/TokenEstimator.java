package bao.gabriel.cognia.common.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Cheap, dependency-free token estimation used for context-window budgeting.
 *
 * <p>It deliberately does not call a tokenizer: CJK characters are counted as
 * one token each (they are close to one token per character for the common
 * tokenizers used by DeepSeek/Qwen/OpenAI), while ASCII text is estimated at
 * four characters per token. An extra per-message overhead covers the chat
 * template markers. The estimate is conservative enough for trimming and
 * compression decisions.</p>
 */
public final class TokenEstimator {

    private static final int MESSAGE_OVERHEAD = 4;
    private static final int REQUEST_OVERHEAD = 3;

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        int length = text.length();
        for (int i = 0; i < length; ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                cjk++;
            } else if (!Character.isWhitespace(codePoint)) {
                other++;
            }
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    public static int estimateMessage(JsonNode message) {
        if (message == null || message.isNull()) {
            return 0;
        }
        int tokens = MESSAGE_OVERHEAD;
        tokens += estimate(message.path("content").asText(""));
        if (message.hasNonNull("tool_calls")) {
            tokens += estimate(message.path("tool_calls").toString());
        }
        return tokens;
    }

    public static int estimateMessages(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return 0;
        }
        int tokens = REQUEST_OVERHEAD;
        for (JsonNode message : messages) {
            tokens += estimateMessage(message);
        }
        return tokens;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL) {
            return true;
        }
        return switch (script) {
            case BOPOMOFO, YI, THAI -> true;
            default -> false;
        };
    }
}
