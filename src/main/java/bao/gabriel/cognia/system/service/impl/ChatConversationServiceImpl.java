package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationMessageResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.entity.ChatMessage;
import bao.gabriel.cognia.system.mapper.ChatConversationMapper;
import bao.gabriel.cognia.system.mapper.ChatMessageMapper;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatConversationServiceImpl implements ChatConversationService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final SiteSettingService siteSettingService;

    @Override
    @Transactional
    public ChatConversation ensure(String conversationId, Long userId, String firstMessage, Long modelId,
                                   List<Long> channelIds) {
        if (StringUtils.hasText(conversationId)) {
            ChatConversation existing = require(conversationId, userId);
            existing.setModelId(modelId);
            existing.setChannelIds(joinIds(channelIds));
            existing.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(existing);
            return existing;
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        conversation.setUserId(userId);
        conversation.setTitle(title(firstMessage));
        conversation.setModelId(modelId);
        conversation.setChannelIds(joinIds(channelIds));
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(conversation.getCreateTime());
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    public List<ConversationSummaryResponse> list(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, userId)
                        .orderByDesc(ChatConversation::getUpdateTime)
                        .orderByDesc(ChatConversation::getId)
                        .last("LIMIT 100"))
                .stream().map(this::toSummary).toList();
    }

    @Override
    public ConversationDetailResponse detail(String conversationId, Long userId) {
        ChatConversation conversation = require(conversationId, userId);
        List<ConversationMessageResponse> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversation.getId())
                        .orderByAsc(ChatMessage::getId))
                .stream()
                .map(message -> new ConversationMessageResponse(message.getRole(), message.getContent(),
                        message.getReasoning(), format(message.getCreateTime())))
                .toList();
        return new ConversationDetailResponse(conversation.getPublicId(), conversation.getTitle(),
                conversation.getModelId(), splitIds(conversation.getChannelIds()), messages,
                format(conversation.getCreateTime()), format(conversation.getUpdateTime()));
    }

    @Override
    @Transactional
    public void append(String conversationId, Long userId, String role, String content, String reasoning) {
        ChatConversation conversation = require(conversationId, userId);
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setRole(role);
        message.setContent(content);
        message.setReasoning(reasoning);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        conversation.setUpdateTime(message.getCreateTime());
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public void delete(String conversationId, Long userId) {
        ChatConversation conversation = require(conversationId, userId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId()));
        conversationMapper.deleteById(conversation.getId());
    }

    private ChatConversation require(String conversationId, Long userId) {
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

    private ConversationSummaryResponse toSummary(ChatConversation conversation) {
        return new ConversationSummaryResponse(conversation.getPublicId(), conversation.getTitle(),
                conversation.getModelId(), splitIds(conversation.getChannelIds()),
                format(conversation.getCreateTime()), format(conversation.getUpdateTime()));
    }

    private String title(String message) {
        String value = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(value)) {
            return "新对话";
        }
        return value.length() <= 60 ? value : value.substring(0, 60) + "…";
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().filter(Objects::nonNull).distinct()
                .map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
    }

    private List<Long> splitIds(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : siteSettingService.formatDateTime(time);
    }
}
