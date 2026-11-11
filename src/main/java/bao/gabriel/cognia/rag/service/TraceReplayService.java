package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.system.dto.trace.ReplayResponse;

/** Re-runs a single node of a recorded chat trace for debugging comparisons. */
public interface TraceReplayService {

    /**
     * @param stage {@code retrieval} or {@code llm}
     */
    ReplayResponse replay(String traceId, String stage, Long operatorId);
}
