package bao.gabriel.cognia.system.dto.chat;

import bao.gabriel.cognia.rag.dto.CitationSource;

import java.util.List;

public record ConversationMessageResponse(
        String id,
        String role,
        String content,
        String reasoning,
        List<CitationSource> citations,
        List<ChatAttachmentRequest> attachments,
        String createTime,
        boolean favorited
) {
}
