package bao.gabriel.cognia.system.dto.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        Long modelId,
        @NotEmpty @Size(max = 100) List<ChatMessageRequest> messages,
        List<Long> channelIds,
        String effort,
        Boolean thinking,
        String conversationId,
        Boolean webSearch,
        Boolean temporary,
        @Size(max = 6) List<ChatAttachmentRequest> attachments,
        Boolean regenerate,
        Integer editMessageIndex,
        /** Admin-only dev mode: record the full chain trace for this request. */
        Boolean debug,
        /** Dev shortcut: override top_k for this request. */
        @Min(1) @Max(100) Integer debugTopK,
        /** Dev shortcut: temporarily disable rerank for this request. */
        Boolean debugRerankEnabled,
        /** Dev shortcut: temporarily disable tool calling for this request. */
        Boolean debugToolsEnabled
) {
}
