package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.rag.dto.RetrievedChunk;

import java.util.List;

public interface RetrievalService {

    List<RetrievedChunk> retrieve(String query, Long viewerId, List<Long> channelIds);

    /**
     * Retrieval with a precomputed query embedding. Lets the chat pipeline embed
     * the query once and reuse the vector for the semantic cache and retrieval.
     */
    List<RetrievedChunk> retrieve(String query, float[] queryVector, Long viewerId, List<Long> channelIds);

    /**
     * Retrieval with dev-mode overrides. {@code topKOverride} replaces the
     * configured candidate count and {@code rerankOverride} forces the rerank
     * stage on or off for a single comparison run.
     */
    List<RetrievedChunk> retrieve(String query, float[] queryVector, Long viewerId, List<Long> channelIds,
                                  Integer topKOverride, Boolean rerankOverride);
}
