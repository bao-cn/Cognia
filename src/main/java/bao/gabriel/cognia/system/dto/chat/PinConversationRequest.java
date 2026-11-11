package bao.gabriel.cognia.system.dto.chat;

import jakarta.validation.constraints.NotNull;

public record PinConversationRequest(@NotNull Boolean pinned) {
}
