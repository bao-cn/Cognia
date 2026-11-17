package bao.gabriel.cognia.common.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure-aware chunker. It keeps headings, paragraphs, tables and fenced
 * code blocks together where possible, and only falls back to sentence
 * boundaries when a single block exceeds the embedding window.
 */
public final class StructuralChunker {

    private StructuralChunker() {}

    public static List<String> chunk(String text, String fileName, int maxChars, int overlapChars) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return List.of();
        int size = Math.max(200, maxChars);
        int overlap = Math.max(0, Math.min(overlapChars, size / 3));
        String ext = extension(fileName);
        List<String> blocks = switch (ext) {
            case "md", "markdown" -> markdownBlocks(normalized);
            case "docx", "doc", "pdf", "xlsx", "xls", "pptx", "ppt", "csv" -> paragraphBlocks(normalized);
            default -> paragraphBlocks(normalized);
        };
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (block.isBlank()) continue;
            if (block.length() > size) {
                flush(current, chunks);
                for (String part : sentenceSplit(block, size)) {
                    if (!part.isBlank()) chunks.add(part);
                }
                continue;
            }
            if (current.length() > 0 && current.length() + 2 + block.length() > size) {
                String previous = current.toString();
                flush(current, chunks);
                if (overlap > 0) {
                    current.append(tail(previous, overlap)).append('\n');
                }
            }
            if (current.length() > 0) current.append('\n');
            current.append(block);
        }
        flush(current, chunks);
        return chunks;
    }

    private static List<String> markdownBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;
        for (String line : text.split("\n", -1)) {
            boolean fence = line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~");
            boolean heading = !inFence && line.matches("^#{1,6}\\s+.*");
            boolean tableStart = !inFence && line.stripLeading().startsWith("|");
            if ((heading || fence) && current.length() > 0) {
                blocks.add(current.toString().trim());
                current.setLength(0);
            }
            if (!inFence && tableStart && current.length() > 0
                    && !current.toString().stripTrailing().endsWith("|")) {
                blocks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(line).append('\n');
            if (fence) inFence = !inFence;
        }
        if (current.length() > 0) blocks.add(current.toString().trim());
        return blocks;
    }

    private static List<String> paragraphBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean previousBlank = false;
        for (String line : text.split("\n", -1)) {
            boolean blank = line.isBlank();
            if (blank) {
                if (!previousBlank) {
                    if (current.length() > 0) blocks.add(current.toString().trim());
                    current.setLength(0);
                }
            } else if (line.matches("^\\s*#{1,6}\\s+.*") || line.stripLeading().startsWith("```")) {
                if (current.length() > 0) blocks.add(current.toString().trim());
                current.setLength(0);
                current.append(line).append('\n');
            } else {
                current.append(line).append('\n');
            }
            previousBlank = blank;
        }
        if (current.length() > 0) blocks.add(current.toString().trim());
        return blocks;
    }

    private static List<String> sentenceSplit(String text, int size) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : text.split("(?<=[。！？!?；;])|(?<=\\.\\s)")) {
            if (sentence.isBlank()) continue;
            if (sentence.length() > size) {
                for (int i = 0; i < sentence.length(); i += size) {
                    result.add(sentence.substring(i, Math.min(sentence.length(), i + size)));
                }
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() > size) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0) result.add(current.toString().trim());
        return result;
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private static String tail(String text, int max) {
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot + 1 < fileName.length() ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
