package bao.gabriel.cognia.system.dto.trace;

import java.util.Map;

public record TraceDetailResponse(
        String traceId,
        String conversationId,
        Long userId,
        String modelName,
        String protocol,
        String question,
        String answer,
        String status,
        Long durationMs,
        String createTime,
        Map<String, Object> trace
) {
}
