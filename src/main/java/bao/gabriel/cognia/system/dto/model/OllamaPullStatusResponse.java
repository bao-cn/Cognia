package bao.gabriel.cognia.system.dto.model;

public record OllamaPullStatusResponse(
        String taskId,
        String model,
        String status,
        String message,
        boolean done,
        Long completed,
        Long total,
        Integer percent
) {
}
