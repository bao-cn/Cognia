package bao.gabriel.cognia.system.dto.chat;

public record PublicChatModel(
        Long id,
        String provider,
        String model,
        String name,
        boolean supportsTools,
        boolean thinking
) {
}
