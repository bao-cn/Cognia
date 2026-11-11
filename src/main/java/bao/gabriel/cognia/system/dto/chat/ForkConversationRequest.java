package bao.gabriel.cognia.system.dto.chat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ForkConversationRequest(@NotNull @Min(0) Integer messageIndex) {
}
