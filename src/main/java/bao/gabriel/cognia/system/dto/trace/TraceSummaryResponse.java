package bao.gabriel.cognia.system.dto.trace;

public record TraceSummaryResponse(
        String traceId,
        String conversationId,
        Long userId,
        String modelName,
        String protocol,
        String question,
        String status,
        Long durationMs,
        String createTime
) {
}
