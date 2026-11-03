package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.service.RagCacheService;
import bao.gabriel.cognia.system.entity.RagSetting;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagCacheServiceImpl implements RagCacheService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong semanticHits = new AtomicLong();
    private final AtomicLong retrievalHits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    @Override
    public String scopeHash(Long viewerId, List<Long> channelIds) {
        List<Long> sorted = channelIds == null ? new ArrayList<>() : new ArrayList<>(channelIds);
        sorted.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        StringBuilder raw = new StringBuilder();
        raw.append(viewerId == null ? "anonymous" : viewerId).append('|');
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                raw.append(',');
            }
            raw.append(sorted.get(i));
        }
        return sha256(raw.toString());
    }

    @Override
    public String cacheKey(String scopeHash, String query) {
        return sha256(scopeHash + "|" + normalize(query));
    }

    @Override
    public SemanticHit findSemantic(String scopeHash, String query, float[] queryEmbedding, RagSetting setting) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return null;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, setting.semanticCacheTtlMinutes()));
        double threshold = setting.semanticCacheThreshold();
        String vector = toVector(queryEmbedding);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, answer, hits,
                           1 - (embedding <=> CAST(:vector AS vector)) AS similarity
                    FROM sys_semantic_cache
                    WHERE scope_hash = :scope
                      AND dimension = :dim
                      AND create_time >= :cutoff
                      AND 1 - (embedding <=> CAST(:vector AS vector)) >= :threshold
                    ORDER BY embedding <=> CAST(:vector AS vector)
                    LIMIT 1
                    """, Map.of("scope", scopeHash, "vector", vector, "dim", queryEmbedding.length,
                    "cutoff", cutoff, "threshold", threshold));
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            long id = ((Number) row.get("id")).longValue();
            long hits = row.get("hits") == null ? 0 : ((Number) row.get("hits")).longValue();
            double similarity = row.get("similarity") == null ? 0 : ((Number) row.get("similarity")).doubleValue();
            String answer = row.get("answer") == null ? "" : row.get("answer").toString();
            touchSemantic(id);
            return new SemanticHit(answer, similarity, cacheKey(scopeHash, query), hits + 1);
        } catch (Exception ex) {
            log.warn("Semantic cache lookup failed: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public RetrievalHit findRetrieval(String scopeHash, String query, RagSetting setting) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return null;
        }
        String queryHash = sha256(normalized);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, setting.retrievalCacheTtlMinutes()));
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, chunks_json, hits
                    FROM sys_retrieval_cache
                    WHERE scope_hash = :scope AND query_hash = :hash AND create_time >= :cutoff
                    ORDER BY last_access_time DESC
                    LIMIT 1
                    """, Map.of("scope", scopeHash, "hash", queryHash, "cutoff", cutoff));
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            long id = ((Number) row.get("id")).longValue();
            long hits = row.get("hits") == null ? 0 : ((Number) row.get("hits")).longValue();
            List<RetrievedChunk> chunks = deserializeChunks(row.get("chunks_json") == null
                    ? null : row.get("chunks_json").toString());
            if (chunks == null) {
                jdbcTemplate.update("DELETE FROM sys_retrieval_cache WHERE id = :id", Map.of("id", id));
                return null;
            }
            touchRetrieval(id);
            String cacheKey = sha256(scopeHash + "|" + normalized);
            return new RetrievalHit(chunks, cacheKey, hits + 1);
        } catch (Exception ex) {
            log.warn("Retrieval cache lookup failed: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public void storeSemanticAsync(String scopeHash, String query, String answer, String contextJson,
                                   float[] embedding, RagSetting setting) {
        if (answer == null || answer.isBlank() || embedding == null || embedding.length == 0) {
            return;
        }
        String vector = toVector(embedding);
        int dimension = embedding.length;
        double threshold = setting.semanticCacheThreshold();
        int ttlMinutes = Math.max(1, setting.semanticCacheTtlMinutes());
        int maxEntries = Math.max(1, setting.semanticCacheMaxEntries());
        executor.submit(() -> {
            try {
                jdbcTemplate.update("""
                        INSERT INTO sys_semantic_cache
                            (scope_hash, query, answer, context_json, embedding, dimension,
                             similarity_threshold, create_time, last_access_time, hits)
                        VALUES (:scope, :query, :answer, :context, CAST(:vector AS vector), :dim,
                                :threshold, NOW(), NOW(), 0)
                        """, Map.of(
                        "scope", scopeHash,
                        "query", query == null ? "" : query,
                        "answer", answer,
                        "context", contextJson == null ? "[]" : contextJson,
                        "vector", vector,
                        "dim", dimension,
                        "threshold", threshold));
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
                jdbcTemplate.update("DELETE FROM sys_semantic_cache WHERE create_time < :cutoff",
                        Map.of("cutoff", cutoff));
                jdbcTemplate.update("""
                        DELETE FROM sys_semantic_cache WHERE id IN (
                            SELECT id FROM sys_semantic_cache ORDER BY last_access_time DESC OFFSET :maxEntries
                        )
                        """, Map.of("maxEntries", maxEntries));
            } catch (Exception ex) {
                log.warn("Semantic cache write failed: {}", ex.getMessage());
            }
        });
    }

    @Override
    public void storeRetrievalAsync(String scopeHash, String query, List<RetrievedChunk> chunks, RagSetting setting) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return;
        }
        String queryHash = sha256(normalized);
        String chunksJson;
        try {
            chunksJson = serializeChunks(chunks);
        } catch (Exception ex) {
            log.warn("Retrieval cache serialization failed: {}", ex.getMessage());
            return;
        }
        Set<Long> documentIds = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk.documentId() != null) {
                documentIds.add(chunk.documentId());
            }
        }
        StringBuilder documents = new StringBuilder();
        for (Long id : documentIds) {
            documents.append(',').append(id);
        }
        if (!documents.isEmpty()) {
            documents.append(',');
        }
        String documentIdsValue = documents.toString();
        int ttlMinutes = Math.max(1, setting.retrievalCacheTtlMinutes());
        int maxEntries = Math.max(1, setting.retrievalCacheMaxEntries());
        executor.submit(() -> {
            try {
                jdbcTemplate.update("""
                        INSERT INTO sys_retrieval_cache
                            (scope_hash, query_hash, normalized_query, chunks_json, document_ids,
                             create_time, last_access_time, hits)
                        VALUES (:scope, :hash, :query, :chunks, :documents, NOW(), NOW(), 0)
                        """, Map.of(
                        "scope", scopeHash,
                        "hash", queryHash,
                        "query", normalized,
                        "chunks", chunksJson,
                        "documents", documentIdsValue));
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
                jdbcTemplate.update("DELETE FROM sys_retrieval_cache WHERE create_time < :cutoff",
                        Map.of("cutoff", cutoff));
                jdbcTemplate.update("""
                        DELETE FROM sys_retrieval_cache WHERE id IN (
                            SELECT id FROM sys_retrieval_cache ORDER BY last_access_time DESC OFFSET :maxEntries
                        )
                        """, Map.of("maxEntries", maxEntries));
            } catch (Exception ex) {
                log.warn("Retrieval cache write failed: {}", ex.getMessage());
            }
        });
    }

    @Override
    public long invalidateDocument(Long documentId) {
        if (documentId == null) {
            return 0;
        }
        try {
            Integer deleted = jdbcTemplate.update(
                    "DELETE FROM sys_retrieval_cache WHERE document_ids LIKE :pattern",
                    Map.of("pattern", "%," + documentId + ",%"));
            log.info("Invalidated {} retrieval cache rows for document {}", deleted, documentId);
            return deleted == null ? 0 : deleted;
        } catch (Exception ex) {
            log.warn("Retrieval cache invalidation failed for document {}: {}", documentId, ex.getMessage());
            return 0;
        }
    }

    @Override
    public long clearSemantic() {
        Integer deleted = jdbcTemplate.update("DELETE FROM sys_semantic_cache", Map.of());
        return deleted == null ? 0 : deleted;
    }

    @Override
    public long clearRetrieval() {
        Integer deleted = jdbcTemplate.update("DELETE FROM sys_retrieval_cache", Map.of());
        return deleted == null ? 0 : deleted;
    }

    @Override
    public Stats stats() {
        long semanticEntries = count("sys_semantic_cache");
        long retrievalEntries = count("sys_retrieval_cache");
        long semantic = semanticHits.get();
        long retrieval = retrievalHits.get();
        long hitTotal = semantic + retrieval;
        long requestTotal = requests.get();
        double hitRate = requestTotal == 0 ? 0 : (double) hitTotal / requestTotal;
        return new Stats(semanticEntries, retrievalEntries, semantic, retrieval, hitTotal,
                misses.get(), requestTotal, Math.round(hitRate * 10000.0) / 10000.0);
    }

    @Override
    public void recordRequest() {
        requests.incrementAndGet();
    }

    @Override
    public void recordHit(String hitType) {
        if ("SEMANTIC".equalsIgnoreCase(hitType)) {
            semanticHits.incrementAndGet();
        } else if ("RETRIEVAL".equalsIgnoreCase(hitType)) {
            retrievalHits.incrementAndGet();
        }
    }

    @Override
    public void recordMiss() {
        misses.incrementAndGet();
    }

    private void touchSemantic(long id) {
        try {
            jdbcTemplate.update("UPDATE sys_semantic_cache SET hits = hits + 1, last_access_time = NOW() "
                    + "WHERE id = :id", Map.of("id", id));
        } catch (Exception ex) {
            log.debug("Semantic cache touch failed: {}", ex.getMessage());
        }
    }

    private void touchRetrieval(long id) {
        try {
            jdbcTemplate.update("UPDATE sys_retrieval_cache SET hits = hits + 1, last_access_time = NOW() "
                    + "WHERE id = :id", Map.of("id", id));
        } catch (Exception ex) {
            log.debug("Retrieval cache touch failed: {}", ex.getMessage());
        }
    }

    private long count(String table) {
        try {
            Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Map.of(), Long.class);
            return value == null ? 0 : value;
        } catch (Exception ex) {
            return 0;
        }
    }

    String serializeChunks(List<RetrievedChunk> chunks) throws Exception {
        ArrayNode array = mapper.createArrayNode();
        for (RetrievedChunk chunk : chunks) {
            ObjectNode node = array.addObject();
            node.put("id", chunk.id());
            node.put("documentId", chunk.documentId());
            node.put("channelId", chunk.channelId());
            node.put("channelName", chunk.channelName());
            node.put("channelSlug", chunk.channelSlug());
            node.put("ownerType", chunk.ownerType());
            node.put("ownerId", chunk.ownerId());
            node.put("channelPublic", chunk.channelPublic());
            node.put("fileName", chunk.fileName());
            node.put("chunkText", chunk.chunkText());
            node.put("chunkIndex", chunk.chunkIndex());
            node.put("pageNumber", chunk.pageNumber());
            node.put("title", chunk.title());
            node.put("author", chunk.author());
            node.put("documentType", chunk.documentType());
            node.put("s3Path", chunk.s3Path());
            node.put("embeddingModel", chunk.embeddingModel());
            node.put("expirationTime", chunk.expirationTime() == null ? null
                    : chunk.expirationTime().toString());
            node.put("expired", chunk.expired());
            node.put("score", chunk.score());
        }
        return mapper.writeValueAsString(array);
    }

    List<RetrievedChunk> deserializeChunks(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode array = mapper.readTree(json);
            if (!array.isArray()) {
                return null;
            }
            List<RetrievedChunk> chunks = new ArrayList<>(array.size());
            for (JsonNode node : array) {
                String expiration = node.path("expirationTime").asText(null);
                LocalDateTime expirationTime = expiration == null || expiration.isBlank()
                        ? null : LocalDateTime.parse(expiration);
                // Recompute expiry: a cached chunk may have expired since it was stored.
                boolean expired = expirationTime != null && !expirationTime.isAfter(LocalDateTime.now());
                chunks.add(new RetrievedChunk(
                        node.path("id").asLong(),
                        node.path("documentId").asLong(),
                        node.path("channelId").asLong(),
                        node.path("channelName").asText(null),
                        node.path("channelSlug").asText(null),
                        node.path("ownerType").asText(null),
                        node.path("ownerId").asLong(),
                        node.path("channelPublic").asBoolean(false),
                        node.path("fileName").asText(null),
                        node.path("chunkText").asText(""),
                        node.path("chunkIndex").asInt(0),
                        node.path("pageNumber").asInt(0),
                        node.path("title").asText(null),
                        node.path("author").asText(null),
                        node.path("documentType").asText(null),
                        node.path("s3Path").asText(null),
                        node.path("embeddingModel").asText(null),
                        expirationTime,
                        expired,
                        node.path("score").asDouble(0)));
            }
            return chunks;
        } catch (Exception ex) {
            log.warn("Retrieval cache deserialization failed: {}", ex.getMessage());
            return null;
        }
    }

    static String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    static String toVector(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
