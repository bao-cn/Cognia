package bao.gabriel.cognia.system.dto.trace;

public record TraceStatsResponse(
        long total,
        long success,
        long failed,
        long truncated,
        long today,
        double avgDurationMs
) {
}
