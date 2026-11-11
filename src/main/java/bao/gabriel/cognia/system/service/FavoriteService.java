package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.favorite.FavoriteListResponse;

import java.util.Set;

public interface FavoriteService {

    FavoriteListResponse list(Long userId);

    boolean addConversation(Long userId, String conversationId);

    boolean removeConversation(Long userId, String conversationId);

    boolean addMessage(Long userId, Long messageId);

    boolean removeMessage(Long userId, Long messageId);

    boolean isConversationFavorited(Long userId, Long conversationId);

    boolean isMessageFavorited(Long userId, Long messageId);

    Set<Long> favoriteConversationIds(Long userId);

    Set<Long> favoriteMessageIds(Long userId);

    void deleteForConversation(Long conversationId);
}
