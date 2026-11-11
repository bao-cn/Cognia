package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.favorite.FavoriteConversationResponse;
import bao.gabriel.cognia.system.dto.favorite.FavoriteListResponse;
import bao.gabriel.cognia.system.dto.favorite.FavoriteMessageResponse;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.entity.ChatMessage;
import bao.gabriel.cognia.system.entity.Favorite;
import bao.gabriel.cognia.system.mapper.ChatConversationMapper;
import bao.gabriel.cognia.system.mapper.ChatMessageMapper;
import bao.gabriel.cognia.system.mapper.FavoriteMapper;
import bao.gabriel.cognia.system.service.FavoriteService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final SiteSettingService siteSettingService;

    @Override
    public FavoriteListResponse list(Long userId) {
        List<Favorite> rows = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime)
                .orderByDesc(Favorite::getId));
        List<FavoriteConversationResponse> conversations = new ArrayList<>();
        List<FavoriteMessageResponse> messages = new ArrayList<>();
        for (Favorite row : rows) {
            ChatConversation conversation = conversationMapper.selectById(row.getConversationId());
            if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
                continue;
            }
            if (Favorite.TYPE_CONVERSATION.equals(row.getItemType())) {
                conversations.add(new FavoriteConversationResponse(
                        String.valueOf(row.getId()), conversation.getPublicId(),
                        StringUtils.hasText(row.getTitle()) ? row.getTitle() : conversation.getTitle(),
                        siteSettingService.formatDateTime(row.getCreateTime())));
            } else if (Favorite.TYPE_MESSAGE.equals(row.getItemType())) {
                messages.add(new FavoriteMessageResponse(
                        String.valueOf(row.getId()), String.valueOf(row.getMessageId()), conversation.getPublicId(),
                        conversation.getTitle(), role(row), content(row),
                        siteSettingService.formatDateTime(row.getCreateTime())));
            }
        }
        return new FavoriteListResponse(conversations, messages);
    }

    @Override
    @Transactional
    public boolean addConversation(Long userId, String conversationId) {
        ChatConversation conversation = requireConversation(conversationId, userId);
        if (isConversationFavorited(userId, conversation.getId())) {
            return true;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setItemType(Favorite.TYPE_CONVERSATION);
        favorite.setConversationId(conversation.getId());
        favorite.setTitle(conversation.getTitle());
        favorite.setCreateTime(LocalDateTime.now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ignored) {
            // Concurrent favorite requests are idempotent.
        }
        return true;
    }

    @Override
    @Transactional
    public boolean removeConversation(Long userId, String conversationId) {
        ChatConversation conversation = requireConversation(conversationId, userId);
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getItemType, Favorite.TYPE_CONVERSATION)
                .eq(Favorite::getConversationId, conversation.getId()));
        return false;
    }

    @Override
    @Transactional
    public boolean addMessage(Long userId, Long messageId) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw BusinessException.notFound("消息不存在");
        }
        ChatConversation conversation = conversationMapper.selectById(message.getConversationId());
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw BusinessException.of(bao.gabriel.cognia.common.api.ResultCode.FORBIDDEN, "无权收藏该消息");
        }
        if (isMessageFavorited(userId, messageId)) {
            return true;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setItemType(Favorite.TYPE_MESSAGE);
        favorite.setConversationId(conversation.getId());
        favorite.setMessageId(message.getId());
        favorite.setTitle(conversation.getTitle());
        favorite.setContent(message.getContent());
        favorite.setCreateTime(LocalDateTime.now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ignored) {
            // Idempotent.
        }
        return true;
    }

    @Override
    @Transactional
    public boolean removeMessage(Long userId, Long messageId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getItemType, Favorite.TYPE_MESSAGE)
                .eq(Favorite::getMessageId, messageId));
        return false;
    }

    @Override
    public boolean isConversationFavorited(Long userId, Long conversationId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getItemType, Favorite.TYPE_CONVERSATION)
                .eq(Favorite::getConversationId, conversationId)) > 0;
    }

    @Override
    public boolean isMessageFavorited(Long userId, Long messageId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getItemType, Favorite.TYPE_MESSAGE)
                .eq(Favorite::getMessageId, messageId)) > 0;
    }

    @Override
    public Set<Long> favoriteConversationIds(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getItemType, Favorite.TYPE_CONVERSATION))
                .stream().map(Favorite::getConversationId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> favoriteMessageIds(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getItemType, Favorite.TYPE_MESSAGE))
                .stream().map(Favorite::getMessageId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void deleteForConversation(Long conversationId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getConversationId, conversationId));
    }

    private ChatConversation requireConversation(String conversationId, Long userId) {
        ChatConversation conversation = StringUtils.hasText(conversationId)
                ? conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getPublicId, conversationId.trim()))
                : null;
        if (conversation == null) {
            throw BusinessException.notFound("对话不存在");
        }
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw BusinessException.of(bao.gabriel.cognia.common.api.ResultCode.FORBIDDEN, "无权访问该对话");
        }
        return conversation;
    }

    private String role(Favorite row) {
        return StringUtils.hasText(row.getContent()) && row.getContent().startsWith("[") ? "assistant" : "assistant";
    }

    private String content(Favorite row) {
        return row.getContent() == null ? "" : row.getContent();
    }
}
