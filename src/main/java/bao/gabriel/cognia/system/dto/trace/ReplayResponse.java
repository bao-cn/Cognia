package bao.gabriel.cognia.system.dto.trace;

import java.util.Map;

/** Result of re-running one trace node from the dev console. */
public record ReplayResponse(
        String traceId,
        String stage,
        String status,
        long durationMs,
        Map<String, Object> result,
        String message
) {
}
