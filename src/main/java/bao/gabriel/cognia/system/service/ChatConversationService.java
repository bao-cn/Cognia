package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse;
import bao.gabriel.cognia.system.entity.ChatConversation;

import java.util.List;

public interface ChatConversationService {

    ChatConversation ensure(String conversationId, Long userId, String firstMessage, Long modelId, List<Long> channelIds);

    List<ConversationSummaryResponse> list(Long userId);

    ConversationDetailResponse detail(String conversationId, Long userId);

    void append(String conversationId, Long userId, String role, String content, String reasoning);

    void delete(String conversationId, Long userId);
}
