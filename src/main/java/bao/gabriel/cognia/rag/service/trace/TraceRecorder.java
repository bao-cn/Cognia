package bao.gabriel.cognia.rag.service.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Mutable, structured record of a single chat request used by the admin dev
 * console. Every section mirrors a stage of the chain so the UI can render it
 * without knowing the engine internals.
 *
 * <p>Instances are created per request and are never shared between threads
 * other than the single virtual thread running the chat.</p>
 */
public class TraceRecorder {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RAW_CHUNKS = 600;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ObjectNode root;
    private final ArrayNode stages;
    private final ObjectNode timing;
    private final long startedNanos = System.nanoTime();
    private long firstTokenNanos = 0;

    public TraceRecorder(String traceId, Long userId, String conversationId) {
        this.root = JSON.createObjectNode();
        this.root.put("traceId", traceId);
        this.root.put("userId", userId);
        this.root.put("conversationId", conversationId == null ? "" : conversationId);
        this.root.put("startedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        this.stages = this.root.putArray("stages");
        this.timing = this.root.putObject("timing");
    }

    public static String newTraceId() {
        return "tr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    public String traceId() {
        return root.path("traceId").asText();
    }

    public ObjectNode root() {
        return root;
    }

    /** Section object, created on demand. */
    public ObjectNode section(String name) {
        JsonNode existing = root.get(name);
        if (existing instanceof ObjectNode object) {
            return object;
        }
        return root.putObject(name);
    }

    public void section(String name, Object value) {
        root.set(name, JSON.valueToTree(value));
    }

    public void put(String section, String key, Object value) {
        ObjectNode node = section(section);
        if (value == null) {
            node.putNull(key);
        } else {
            node.set(key, JSON.valueToTree(value));
        }
    }

    public void set(String section, String key, Object value) {
        section(section).set(key, JSON.valueToTree(value));
    }

    public void error(String section, String message) {
        section(section).put("error", message == null ? "" : message);
        section("error").put("section", section);
        section("error").put("message", message == null ? "" : message);
    }

    /** Appends one stage entry with the elapsed time since its start. */
    public void stage(String name, long startedNanos, String status, String detail) {
        ObjectNode stage = stages.addObject();
        stage.put("name", name);
        stage.put("status", status);
        stage.put("startMs", (startedNanos - this.startedNanos) / 1_000_000);
        stage.put("durationMs", (System.nanoTime() - startedNanos) / 1_000_000);
        if (detail != null) {
            stage.put("detail", detail);
        }
    }

    public void stage(String name, long startedNanos, String status) {
        stage(name, startedNanos, status, null);
    }

    public void firstToken() {
        if (firstTokenNanos == 0) {
            firstTokenNanos = System.nanoTime();
            timing.put("firstTokenMs", (firstTokenNanos - startedNanos) / 1_000_000);
        }
    }

    /** Appends one streamed chunk, bounded so traces stay small. */
    public void rawChunk(String kind, String text) {
        ObjectNode llm = section("llm");
        ArrayNode chunks = llm.get("chunks") instanceof ArrayNode array ? array : llm.putArray("chunks");
        if (chunks.size() >= MAX_RAW_CHUNKS || text == null || text.isEmpty()) {
            return;
        }
        ObjectNode chunk = chunks.addObject();
        chunk.put("t", LocalDateTime.now().format(TIME));
        chunk.put("kind", kind);
        chunk.put("text", text.length() > 4000 ? text.substring(0, 4000) : text);
    }

    public void rawPayload(String json) {
        ObjectNode llm = section("llm");
        ArrayNode raws = llm.get("raw") instanceof ArrayNode array ? array : llm.putArray("raw");
        if (raws.size() >= MAX_RAW_CHUNKS || json == null) {
            return;
        }
        raws.add(json.length() > 4000 ? json.substring(0, 4000) : json);
    }

    public long durationMs() {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    public void finishTiming() {
        timing.put("totalMs", durationMs());
        if (firstTokenNanos > 0) {
            timing.put("firstTokenMs", (firstTokenNanos - startedNanos) / 1_000_000);
        }
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(root);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
