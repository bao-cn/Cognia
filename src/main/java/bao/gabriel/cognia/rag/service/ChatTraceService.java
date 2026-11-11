package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.rag.service.trace.TraceRecorder;
import bao.gabriel.cognia.system.dto.trace.TraceDetailResponse;
import bao.gabriel.cognia.system.dto.trace.TraceStatsResponse;
import bao.gabriel.cognia.system.dto.trace.TraceSummaryResponse;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;

public interface ChatTraceService {

    /** Starts a recorder when dev mode is enabled, otherwise returns null. */
    TraceRecorder start(Long userId, String conversationId, ResolvedModel model, String question, boolean debug);

    /** Persists a finished trace (no-op for null recorders). */
    void finish(TraceRecorder recorder, String status, String answer);

    PageResult<TraceSummaryResponse> page(long page, long size, String status, String keyword);

    TraceStatsResponse stats();

    TraceDetailResponse detail(String traceId);

    void clearAll();
}
