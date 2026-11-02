package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.dto.RetrievedChunk;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.RetrievalService;
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

    @Override
    public List<RetrievedChunk> retrieve(String query, Long viewerId, List<Long> channelIds) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        RagSetting setting = ragSettingService.getSetting();
        int topK = Math.max(setting.retrievalTopK(), setting.retrievalTopN());
        int topN = Math.max(1, setting.retrievalTopN());
        EmbeddingService.EmbeddedText embedded = embeddingService.embed(query);
        String vector = toVector(embedded.vector());

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
                  AND (c.is_public = TRUE
                       OR (c.owner_type = 'USER' AND c.owner_id = :viewerId)
                       OR (c.owner_type = 'TEAM' AND EXISTS (
                           SELECT 1 FROM sys_team_member tm
                           WHERE tm.team_id = c.owner_id AND tm.user_id = :viewerId)))
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("vector", vector);
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
            log.warn("Vector retrieval failed: {}", ex.getMessage(), ex);
            return List.of();
        }
        if (candidates.isEmpty()) {
            log.warn("Vector retrieval returned no chunks for viewer {} channels {}", viewerId, channelIds);
            return List.of();
        }
        if (setting.rerankEnabled() && setting.getRerankModelId() != null) {
            List<RetrievedChunk> reranked = rerank(query, candidates, setting.getRerankModelId(), topN);
            if (!reranked.isEmpty()) {
                return reranked;
            }
        }
        candidates.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return candidates.subList(0, Math.min(topN, candidates.size()));
    }

    private List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, Long modelId, int topN) {
        try {
            ResolvedModel model = modelConfigService.resolveModel(modelId);
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
            log.warn("Rerank model failed, using local lexical rerank: {}", ex.getMessage());
            return localRerank(query, candidates, topN);
        }
    }

    private List<RetrievedChunk> localRerank(String query, List<RetrievedChunk> candidates, int topN) {
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
