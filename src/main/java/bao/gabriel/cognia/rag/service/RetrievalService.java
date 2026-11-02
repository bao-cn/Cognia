package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.rag.dto.RetrievedChunk;

import java.util.List;

public interface RetrievalService {

    List<RetrievedChunk> retrieve(String query, Long viewerId, List<Long> channelIds);
}
