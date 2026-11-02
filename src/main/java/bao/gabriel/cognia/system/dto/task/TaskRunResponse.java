package bao.gabriel.cognia.system.dto.task;

public record TaskRunResponse(
        String code,
        String status,
        String message,
        int durationMs,
        String ranAt
) {
}
