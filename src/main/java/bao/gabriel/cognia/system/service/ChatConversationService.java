package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse;
import bao.gabriel.cognia.system.entity.ChatConversation;

import java.util.List;

public interface ChatConversationService {

    ChatConversation ensure(String conversationId, Long userId, String firstMessage, Long modelId, List<Long> channelIds);

    List<ConversationSummaryResponse> list(Long userId, boolean archived);

    void archive(String conversationId, Long userId, boolean archived);

    ConversationDetailResponse detail(String conversationId, Long userId);

    Long append(String conversationId, Long userId, String role, String content, String reasoning,
                String citationsJson, String attachmentsJson);

    default Long append(String conversationId, Long userId, String role, String content, String reasoning) {
        return append(conversationId, userId, role, content, reasoning, null, null);
    }

    void delete(String conversationId, Long userId);

    void setPinned(String conversationId, Long userId, boolean pinned);

    ConversationDetailResponse fork(String conversationId, Long userId, int messageCount);

    void removeLastAssistant(String conversationId, Long userId);

    void truncateFrom(String conversationId, Long userId, int messageIndex);

    void generateTitleAsync(String conversationId, Long modelId, String userMessage, String assistantMessage);
}
