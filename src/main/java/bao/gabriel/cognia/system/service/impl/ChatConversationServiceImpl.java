package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationMessageResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.entity.ChatMessage;
import bao.gabriel.cognia.system.mapper.ChatConversationMapper;
import bao.gabriel.cognia.system.mapper.ChatMessageMapper;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.RagSettingService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationServiceImpl implements ChatConversationService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final SiteSettingService siteSettingService;
    private final RagSettingService ragSettingService;
    private final ModelConfigService modelConfigService;
    private final OpenAiCompatibleClient aiClient;

    private static final String TITLE_PROMPT = """
            你是对话标题生成器。请根据用户的第一条消息和助手的回答，生成一个简洁、具体的中文对话标题。
            要求：
            1. 4 到 20 个汉字，最多 24 个字符；
            2. 概括对话主题，不要复述原句，不要包含“对话”“标题”等前缀；
            3. 不要使用引号、书名号、句号或解释；
            4. 只输出标题本身。""";

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

    @Override
    public void generateTitleAsync(String conversationId, Long modelId, String userMessage, String assistantMessage) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        Thread.ofVirtual().name("chat-title-" + conversationId).start(() -> {
            try {
                if (!ragSettingService.getSetting().titleGenerationEnabled()) {
                    return;
                }
                ChatConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getPublicId, conversationId.trim()));
                if (conversation == null) {
                    return;
                }
                ResolvedModel model = modelId != null
                        ? modelConfigService.resolveModel(modelId)
                        : modelConfigService.resolveChat(null);
                ObjectNode body = aiClient.mapper().createObjectNode();
                body.put("model", model.modelId());
                body.put("stream", false);
                body.put("temperature", 0.2);
                body.put("max_tokens", 48);
                ArrayNode messages = body.putArray("messages");
                messages.addObject().put("role", "system").put("content", TITLE_PROMPT);
                messages.addObject().put("role", "user").put("content",
                        "用户：\n" + truncate(userMessage, 1200) + "\n\n助手：\n" + truncate(assistantMessage, 1200));

                JsonNode extra = modelConfigService.extraBody(model, null);
                if (extra != null && extra.isObject()) {
                    extra.fields().forEachRemaining(entry -> body.set(entry.getKey(), entry.getValue()));
                }
                // Title generation should be fast; force vendor thinking off.
                if ("DEEPSEEK".equals(model.vendor())) {
                    body.putObject("thinking").put("type", "disabled");
                    body.remove("reasoning_effort");
                } else if ("QWEN".equals(model.vendor())) {
                    body.put("enable_thinking", false);
                    body.remove("reasoning_effort");
                }

                JsonNode response = aiClient.postJson(model.baseUrl(), "/chat/completions",
                        model.apiKey(), body, Duration.ofSeconds(30));
                String content = response.path("choices").path(0).path("message").path("content").asText("");
                String title = cleanTitle(content);
                if (!StringUtils.hasText(title)) {
                    return;
                }
                ChatConversation update = new ChatConversation();
                update.setId(conversation.getId());
                update.setTitle(title);
                update.setUpdateTime(LocalDateTime.now());
                conversationMapper.updateById(update);
            } catch (Exception ex) {
                log.warn("Unable to generate conversation title: {}", ex.getMessage());
            }
        });
    }

    private String cleanTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String title = raw
                .replaceAll("(?i)^\\s*标题\\s*[:：]\\s*", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("^[\\s\"'“”‘’`]+", "")
                .replaceAll("[\\s\"'“”‘’`。.!！]+$", "")
                .trim();
        if (!StringUtils.hasText(title)) {
            return null;
        }
        return title.length() <= 24 ? title : title.substring(0, 24) + "…";
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
