package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.dto.RetrievalOutcome;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.RetrievalService;
import bao.gabriel.cognia.rag.ingestion.IngestionSetting;
import bao.gabriel.cognia.rag.ingestion.IngestionSettingService;
import bao.gabriel.cognia.system.entity.ModelConfig;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import bao.gabriel.cognia.system.service.RagSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final EmbeddingService embeddingService;
    private final RagSettingService ragSettingService;
    private final ModelConfigService modelConfigService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OpenAiCompatibleClient client;
    private final IngestionSettingService ingestionSettingService;

    @Override
    public List<RetrievedChunk> retrieve(String query, Long viewerId, List<Long> channelIds) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        EmbeddingService.EmbeddedText embedded = embeddingService.embed(query);
        return retrieve(query, embedded.vector(), viewerId, channelIds);
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, float[] queryVector, Long viewerId, List<Long> channelIds) {
        return retrieve(query, queryVector, viewerId, channelIds, null, null);
    }

    @Override
    @Transactional
    public List<RetrievedChunk> retrieve(String query, float[] queryVector, Long viewerId, List<Long> channelIds,
                                         Integer topKOverride, Boolean rerankOverride) {
        return retrieveOutcome(query, queryVector, viewerId, channelIds, topKOverride, rerankOverride).chunks();
    }

    @Override
    @Transactional
    public RetrievalOutcome retrieveOutcome(String query, float[] queryVector, Long viewerId, List<Long> channelIds,
                                            Integer topKOverride, Boolean rerankOverride) {
        if (!StringUtils.hasText(query) || queryVector == null || queryVector.length == 0) {
            return new RetrievalOutcome(List.of(), false, 0, null);
        }
        RagSetting setting = ragSettingService.getSetting();
        String activeModel = embeddingService.activeModelLabel();
        if (activeModel == null) {
            log.warn("No embedding model configured; vector retrieval skipped");
            return new RetrievalOutcome(List.of(), false, 0, "NO_EMBEDDING_MODEL");
        }
        IngestionSetting ingestion = ingestionSettingService.get();
        jdbcTemplate.getJdbcTemplate().execute("SET LOCAL hnsw.ef_search = " + ingestion.hnswEfSearch());
        int topK = topKOverride != null
                ? Math.max(1, Math.min(topKOverride, 200))
                : Math.max(setting.retrievalTopK(), setting.retrievalTopN());
        int topN = Math.max(1, setting.retrievalTopN());
        String vector = toVector(queryVector);

        StringBuilder sql = new StringBuilder("""
                SELECT dc.id, dc.document_id, dc.chunk_text, dc.chunk_index, dc.page_number,
                       dc.title, dc.author, dc.document_type, dc.s3_path, dc.embedding_model,
                       dc.expiration_time, d.name AS file_name, d.channel_id,
                       c.name AS channel_name, c.slug AS channel_slug, c.owner_type, c.owner_id,
                       c.is_public,
                       1 - (dc.embedding <=> CAST(:vector AS vector)) AS vector_score
                FROM document_chunk dc
                JOIN kb_document d ON d.id = dc.document_id AND d.status = 'READY'
                JOIN kb_channel c ON c.id = d.channel_id
                WHERE dc.embedding IS NOT NULL
                  AND dc.embedding_status = 'READY'
                  AND dc.embedding_model = :activeModel
                  AND (c.is_public = TRUE
                       OR (c.owner_type = 'USER' AND c.owner_id = :viewerId)
                       OR (c.owner_type = 'TEAM' AND EXISTS (
                           SELECT 1 FROM sys_team_member tm
                           WHERE tm.team_id = c.owner_id AND tm.user_id = :viewerId)))
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("vector", vector);
        params.addValue("activeModel", activeModel);
        params.addValue("viewerId", viewerId == null ? -1L : viewerId);
        if (channelIds != null && !channelIds.isEmpty()) {
            sql.append(" AND d.channel_id IN (:channelIds)");
            params.addValue("channelIds", channelIds);
        }
        sql.append(" ORDER BY dc.embedding <=> CAST(:vector AS vector) LIMIT :limit");
        params.addValue("limit", topK);

        List<RetrievedChunk> candidates = new ArrayList<>();
        try {
            jdbcTemplate.query(sql.toString(), params, rs -> {
                LocalDateTime expiration = rs.getTimestamp("expiration_time") == null
                        ? null : rs.getTimestamp("expiration_time").toLocalDateTime();
                boolean expired = expiration != null && expiration.isBefore(LocalDateTime.now());
                candidates.add(new RetrievedChunk(
                        rs.getLong("id"), rs.getLong("document_id"), rs.getLong("channel_id"),
                        rs.getString("channel_name"), rs.getString("channel_slug"),
                        rs.getString("owner_type"), rs.getLong("owner_id"), rs.getBoolean("is_public"),
                        rs.getString("file_name"), rs.getString("chunk_text"), rs.getInt("chunk_index"),
                        rs.getInt("page_number"), rs.getString("title"), rs.getString("author"),
                        rs.getString("document_type"), rs.getString("s3_path"),
                        rs.getString("embedding_model"), expiration, expired,
                        rs.getDouble("vector_score")));
            });
        } catch (Exception ex) {
            log.error("Vector retrieval failed", ex);
            return new RetrievalOutcome(List.of(), false, 0, "VECTOR_RETRIEVAL_FAILED");
        }
        if (candidates.isEmpty()) {
            log.warn("Vector retrieval returned no chunks for viewer {} channels {}", viewerId, channelIds);
            return new RetrievalOutcome(List.of(), false, 0, null);
        }
        boolean rerankOn = rerankOverride != null ? rerankOverride : setting.rerankEnabled();
        if (rerankOn && setting.getRerankModelId() != null) {
            long started = System.nanoTime();
            try {
                List<RetrievedChunk> reranked = rerank(query, candidates, setting.getRerankModelId(), topN);
                long duration = (System.nanoTime() - started) / 1_000_000;
                if (!reranked.isEmpty()) {
                    return new RetrievalOutcome(reranked, true, duration, null);
                }
                log.warn("Rerank returned no usable scores; using lexical weighting");
                List<RetrievedChunk> degraded = lexicalWeighted(query, candidates, topN);
                return new RetrievalOutcome(degraded, false, duration, "RERANK_EMPTY");
            } catch (Exception ex) {
                long duration = (System.nanoTime() - started) / 1_000_000;
                log.warn("Rerank failed after {} ms: {}", duration, ex.getMessage());
                List<RetrievedChunk> degraded = lexicalWeighted(query, candidates, topN);
                return new RetrievalOutcome(degraded, false, duration,
                        "RERANK_FAILED: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        return new RetrievalOutcome(lexicalWeighted(query, candidates, topN), false, 0, null);
    }

    private List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, Long modelId, int topN) {
        try {
            ResolvedModel model = modelConfigService.resolveModel(modelId);
            if ("OLLAMA".equalsIgnoreCase(model.vendor())) {
                return ollamaRerank(query, candidates, model, topN);
            }
            ObjectNode body = client.mapper().createObjectNode();
            body.put("model", model.modelId());
            body.put("query", query);
            ArrayNode documents = body.putArray("documents");
            for (RetrievedChunk candidate : candidates) {
                documents.add(candidate.chunkText());
            }
            body.put("top_n", Math.min(topN, candidates.size()));
            JsonNode response = client.postJson(model.baseUrl(), "/rerank", model.apiKey(), body,
                    java.time.Duration.ofSeconds(60));
            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                results = response.path("data");
            }
            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }
            List<RetrievedChunk> reranked = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                double score = item.path("relevance_score").asDouble(item.path("score").asDouble(0));
                RetrievedChunk origin = candidates.get(index);
                reranked.add(new RetrievedChunk(origin.id(), origin.documentId(), origin.channelId(),
                        origin.channelName(), origin.channelSlug(), origin.ownerType(), origin.ownerId(),
                        origin.channelPublic(), origin.fileName(), origin.chunkText(), origin.chunkIndex(),
                        origin.pageNumber(), origin.title(), origin.author(), origin.documentType(),
                        origin.s3Path(), origin.embeddingModel(), origin.expirationTime(), origin.expired(), score));
            }
            return reranked.isEmpty() ? List.of() : reranked.subList(0, Math.min(topN, reranked.size()));
        } catch (Exception ex) {
            throw new IllegalStateException("rerank provider error: " + ex.getMessage(), ex);
        }
    }


    private List<RetrievedChunk> ollamaRerank(String query, List<RetrievedChunk> candidates,
                                             ResolvedModel model, int topN) {
        try {
            ObjectNode body = client.mapper().createObjectNode();
            body.put("model", model.modelId());
            body.put("stream", false);
            body.put("format", "json");
            var messages = body.putArray("messages");
            var system = messages.addObject();
            system.put("role", "system");
            system.put("content", "You are a reranker. Score each document for relevance to the query from 0 to 1. "
                    + "Return only a JSON array of {index,score} objects, one per document.");
            var user = messages.addObject();
            user.put("role", "user");
            StringBuilder prompt = new StringBuilder("Query: ").append(query).append("\nDocuments:\n");
            for (int i = 0; i < candidates.size(); i++) {
                prompt.append("[").append(i).append("] ").append(candidates.get(i).chunkText()).append("\n");
            }
            user.put("content", prompt.toString());
            ObjectNode options = body.putObject("options");
            options.put("temperature", 0);
            String base = model.baseUrl().replaceAll("/v1/?$", "").replaceAll("/+$", "");
            JsonNode response = client.postJson(base, "/api/chat", model.apiKey(), body,
                    java.time.Duration.ofSeconds(120));
            String content = response.path("message").path("content").asText("");
            JsonNode scores = client.mapper().readTree(content);
            if (!scores.isArray()) return List.of();
            List<RetrievedChunk> reranked = new ArrayList<>();
            for (JsonNode item : scores) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= candidates.size()) continue;
                double score = item.path("score").asDouble(0);
                RetrievedChunk origin = candidates.get(index);
                reranked.add(new RetrievedChunk(origin.id(), origin.documentId(), origin.channelId(),
                        origin.channelName(), origin.channelSlug(), origin.ownerType(), origin.ownerId(),
                        origin.channelPublic(), origin.fileName(), origin.chunkText(), origin.chunkIndex(),
                        origin.pageNumber(), origin.title(), origin.author(), origin.documentType(),
                        origin.s3Path(), origin.embeddingModel(), origin.expirationTime(), origin.expired(), score));
            }
            reranked.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
            return reranked.isEmpty() ? List.of() : reranked.subList(0, Math.min(topN, reranked.size()));
        } catch (Exception ex) {
            throw new IllegalStateException("ollama rerank error: " + ex.getMessage(), ex);
        }
    }
    private List<RetrievedChunk> lexicalWeighted(String query, List<RetrievedChunk> candidates, int topN) {
        List<String> queryTerms = terms(query);
        List<RetrievedChunk> scored = new ArrayList<>(candidates.size());
        for (RetrievedChunk candidate : candidates) {
            List<String> terms = terms(candidate.chunkText());
            long matches = queryTerms.stream().distinct().filter(terms::contains).count();
            double lexical = queryTerms.isEmpty() ? 0 : (double) matches / queryTerms.size();
            double score = candidate.score() * 0.7 + lexical * 0.3;
            scored.add(new RetrievedChunk(candidate.id(), candidate.documentId(), candidate.channelId(),
                    candidate.channelName(), candidate.channelSlug(), candidate.ownerType(), candidate.ownerId(),
                    candidate.channelPublic(), candidate.fileName(), candidate.chunkText(), candidate.chunkIndex(),
                    candidate.pageNumber(), candidate.title(), candidate.author(), candidate.documentType(),
                    candidate.s3Path(), candidate.embeddingModel(), candidate.expirationTime(), candidate.expired(), score));
        }
        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return scored.subList(0, Math.min(topN, scored.size()));
    }

    private List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private String toVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
