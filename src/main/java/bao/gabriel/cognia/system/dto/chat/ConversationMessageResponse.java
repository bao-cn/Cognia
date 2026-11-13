package bao.gabriel.cognia.system.dto.chat;

import bao.gabriel.cognia.rag.dto.CitationSource;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

public record ConversationMessageResponse(
        String id,
        String role,
        String content,
        String reasoning,
        List<CitationSource> citations,
        List<ChatAttachmentRequest> attachments,
        @JsonIgnore String contextText,
        String createTime,
        boolean favorited,
        Map<String, Object> metrics
) {
}
