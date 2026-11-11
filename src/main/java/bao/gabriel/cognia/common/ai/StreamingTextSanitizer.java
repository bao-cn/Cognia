package bao.gabriel.cognia.common.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes DeepSeek DSML tool-call markup that occasionally leaks into
 * {@code content} / {@code reasoning_content} instead of the structured
 * {@code tool_calls} field. Such markup is internal protocol text and must never
 * reach the user or be persisted as part of an answer.
 *
 * <p>DeepSeek has emitted the marker with ASCII pipes ({@code <|DSML|}) and
 * full-width pipes ({@code <｜｜DSML｜｜}). Because a marker, its line, or the
 * following protocol lines can be split across SSE chunks, the sanitizer keeps
 * a block state: after a marker it drops the rest of that line and any
 * subsequent protocol lines ({@code calls>}, {@code invoke ...},
 * {@code parameter ...}, closing marker) until normal prose resumes.</p>
 */
public class StreamingTextSanitizer {

    /** A DSML marker plus the rest of its line. */
    private static final Pattern DSML_LINE = Pattern.compile(
            "(?i)<\\s*/?\\s*[|｜]\\s*[|｜]?\\s*DSML[^\\n]*");

    /** Protocol continuation line after a marker was split from its content. */
    private static final Pattern PROTOCOL_LINE = Pattern.compile(
            "(?i)^\\s*(calls|invoke|parameter)\\b.*");

    /** A suffix that may still grow into a DSML marker. */
    private static final Pattern PARTIAL_MARKER = Pattern.compile(
            "(?i)^<\\s*/?\\s*([|｜]\\s*[|｜]?\\s*)?(D(S(M(L)?)?)?)?\\s*$");

    private static final int MAX_HOLD = 48;

    private final StringBuilder buffer = new StringBuilder();
    private boolean inBlock;

    public String accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        buffer.append(delta);
        StringBuilder out = new StringBuilder();

        while (true) {
            if (inBlock) {
                int newline = buffer.indexOf("\n");
                if (newline < 0) {
                    // Still inside the protocol block; hold everything.
                    break;
                }
                String line = buffer.substring(0, newline);
                buffer.delete(0, newline + 1);
                if (isProtocolLine(line)) {
                    continue;
                }
                // Normal prose ends the leaked block.
                inBlock = false;
                out.append(line).append('\n');
                continue;
            }

            Matcher matcher = DSML_LINE.matcher(buffer);
            if (!matcher.find()) {
                break;
            }
            out.append(buffer, 0, matcher.start());
            buffer.delete(0, matcher.end());
            inBlock = true;
        }

        if (!inBlock) {
            String text = buffer.toString();
            int hold = holdStart(text);
            int end = text.length() - hold;
            out.append(text, 0, end);
            buffer.setLength(0);
            buffer.append(text.substring(end));
        }
        return out.toString();
    }

    public String flush() {
        String text = buffer.toString();
        buffer.setLength(0);
        if (inBlock) {
            inBlock = false;
            return "";
        }
        return DSML_LINE.matcher(text).replaceAll("");
    }

    private boolean isProtocolLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if (trimmed.contains("DSML") || trimmed.startsWith("</")) {
            return true;
        }
        return PROTOCOL_LINE.matcher(trimmed).matches();
    }

    private int holdStart(String text) {
        int lt = text.lastIndexOf('<');
        if (lt < 0) {
            return 0;
        }
        String suffix = text.substring(lt);
        if (suffix.length() > MAX_HOLD || !PARTIAL_MARKER.matcher(suffix).matches()) {
            return 0;
        }
        return suffix.length();
    }
}
