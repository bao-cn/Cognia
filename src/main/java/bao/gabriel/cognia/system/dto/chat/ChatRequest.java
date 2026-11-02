package bao.gabriel.cognia.system.dto.chat;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        Long modelId,
        @NotEmpty @Size(max = 100) List<ChatMessageRequest> messages,
        List<Long> channelIds,
        String effort,
        Boolean thinking,
        String conversationId
) {
}
