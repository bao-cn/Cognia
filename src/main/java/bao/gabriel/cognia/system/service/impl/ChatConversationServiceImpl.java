package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse;
import bao.gabriel.cognia.rag.dto.CitationSource;
import bao.gabriel.cognia.system.dto.chat.ChatAttachmentRequest;
import bao.gabriel.cognia.system.dto.chat.ConversationMessageResponse;
import bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.entity.ChatMessage;
import bao.gabriel.cognia.system.entity.Favorite;
import bao.gabriel.cognia.system.mapper.ChatConversationMapper;
import bao.gabriel.cognia.system.mapper.ChatMessageMapper;
import bao.gabriel.cognia.system.mapper.FavoriteMapper;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.RagSettingService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationServiceImpl implements ChatConversationService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final FavoriteMapper favoriteMapper;
    private final SiteSettingService siteSettingService;
    private final RagSettingService ragSettingService;
    private final ModelConfigService modelConfigService;
    private final ObjectStorageService objectStorageService;
    private final OpenAiCompatibleClient aiClient;
    private static final ObjectMapper JSON = new ObjectMapper();

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
            if (Boolean.TRUE.equals(existing.getArchived())) {
                throw BusinessException.badRequest("对话已归档，请先恢复后再继续");
            }
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
    public List<ConversationSummaryResponse> list(Long userId, boolean archived) {
        Set<Long> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getItemType, Favorite.TYPE_CONVERSATION))
                .stream().map(Favorite::getConversationId).collect(java.util.stream.Collectors.toSet());
        return conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getUserId, userId)
                        .eq(ChatConversation::getArchived, archived)
                        .orderByDesc(ChatConversation::getPinned)
                        .orderByDesc(ChatConversation::getPinnedTime)
                        .orderByDesc(ChatConversation::getUpdateTime)
                        .orderByDesc(ChatConversation::getId)
                        .last("LIMIT 100"))
                .stream().map(conversation -> toSummary(conversation, favorites)).toList();
    }

    @Override
    public ConversationDetailResponse detail(String conversationId, Long userId) {
        ChatConversation conversation = require(conversationId, userId);
        Set<Long> favoriteMessages = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getItemType, Favorite.TYPE_MESSAGE))
                .stream().map(Favorite::getMessageId).collect(java.util.stream.Collectors.toSet());
        List<ConversationMessageResponse> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversation.getId())
                        .orderByAsc(ChatMessage::getId))
                .stream()
                .map(message -> new ConversationMessageResponse(String.valueOf(message.getId()), message.getRole(),
                        message.getContent(), message.getReasoning(),
                        usedCitations(message.getContent(), parseCitations(message.getCitationsJson())),
                        parseAttachments(message.getAttachmentsJson()),
                        format(message.getCreateTime()), favoriteMessages.contains(message.getId())))
                .toList();
        return new ConversationDetailResponse(conversation.getPublicId(), conversation.getTitle(),
                conversation.getModelId(), splitIds(conversation.getChannelIds()), messages,
                format(conversation.getCreateTime()), format(conversation.getUpdateTime()),
                Boolean.TRUE.equals(conversation.getArchived()));
    }

    @Override
    @Transactional
    public Long append(String conversationId, Long userId, String role, String content, String reasoning,
                       String citationsJson, String attachmentsJson) {
        ChatConversation conversation = requireActive(conversationId, userId);
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setRole(role);
        message.setContent(content);
        message.setReasoning(reasoning);
        message.setCitationsJson(citationsJson);
        message.setAttachmentsJson(attachmentsJson);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        conversation.setUpdateTime(message.getCreateTime());
        conversationMapper.updateById(conversation);
        return message.getId();
    }

    @Override
    @Transactional
    public void delete(String conversationId, Long userId) {
        // Deprecated: conversations are archived, never deleted by users.
        archive(conversationId, userId, true);
    }

    @Override
    @Transactional
    public void archive(String conversationId, Long userId, boolean archived) {
        ChatConversation conversation = require(conversationId, userId);
        conversation.setArchived(archived);
        conversation.setArchivedTime(archived ? LocalDateTime.now() : null);
        if (archived) {
            conversation.setPinned(false);
            conversation.setPinnedTime(null);
        }
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public void setPinned(String conversationId, Long userId, boolean pinned) {
        ChatConversation conversation = requireActive(conversationId, userId);
        if (pinned && !Boolean.TRUE.equals(conversation.getPinned())) {
            long count = conversationMapper.selectCount(new LambdaQueryWrapper<ChatConversation>()
                    .eq(ChatConversation::getUserId, userId)
                    .eq(ChatConversation::getPinned, true));
            if (count >= 5) {
                throw BusinessException.badRequest("最多只能置顶 5 条对话");
            }
        }
        conversation.setPinned(pinned);
        conversation.setPinnedTime(pinned ? LocalDateTime.now() : null);
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public ConversationDetailResponse fork(String conversationId, Long userId, int messageCount) {
        ChatConversation source = requireActive(conversationId, userId);
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, source.getId())
                .orderByAsc(ChatMessage::getId));
        int count = Math.max(0, Math.min(messageCount, messages.size()));
        if (count == 0) {
            throw BusinessException.badRequest("分叉位置无效");
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        String baseTitle = source.getTitle() == null ? "新对话" : source.getTitle();
        String suffix = " · 分支";
        conversation.setTitle(baseTitle.length() + suffix.length() <= 60
                ? baseTitle + suffix : baseTitle.substring(0, Math.max(1, 60 - suffix.length())) + suffix);
        conversation.setUserId(userId);
        conversation.setModelId(source.getModelId());
        conversation.setChannelIds(source.getChannelIds());
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(conversation.getCreateTime());
        conversationMapper.insert(conversation);
        for (int i = 0; i < count; i++) {
            ChatMessage original = messages.get(i);
            ChatMessage copy = new ChatMessage();
            copy.setConversationId(conversation.getId());
            copy.setRole(original.getRole());
            copy.setContent(original.getContent());
            copy.setReasoning(original.getReasoning());
            copy.setCitationsJson(original.getCitationsJson());
            copy.setCreateTime(LocalDateTime.now());
            messageMapper.insert(copy);
        }
        return detail(conversation.getPublicId(), userId);
    }

    @Override
    @Transactional
    public void removeLastAssistant(String conversationId, Long userId) {
        ChatConversation conversation = requireActive(conversationId, userId);
        ChatMessage last = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getRole, "assistant")
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        if (last != null) {
            messageMapper.deleteById(last.getId());
        }
    }

    @Override
    @Transactional
    public void truncateFrom(String conversationId, Long userId, int messageIndex) {
        ChatConversation conversation = requireActive(conversationId, userId);
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .orderByAsc(ChatMessage::getId));
        if (messageIndex < 0 || messageIndex >= messages.size()) {
            throw bao.gabriel.cognia.common.exception.BusinessException.badRequest("待编辑的消息不存在");
        }
        ChatMessage target = messages.get(messageIndex);
        if (!"user".equals(target.getRole())) {
            throw bao.gabriel.cognia.common.exception.BusinessException.badRequest("只能编辑用户消息");
        }
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .ge(ChatMessage::getId, target.getId()));
        conversation.setUpdateTime(java.time.LocalDateTime.now());
        conversationMapper.updateById(conversation);
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

    private ChatConversation requireActive(String conversationId, Long userId) {
        ChatConversation conversation = require(conversationId, userId);
        if (Boolean.TRUE.equals(conversation.getArchived())) {
            throw BusinessException.badRequest("对话已归档，请先恢复后再操作");
        }
        return conversation;
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

    private ConversationSummaryResponse toSummary(ChatConversation conversation, Set<Long> favorites) {
        return new ConversationSummaryResponse(conversation.getPublicId(), conversation.getTitle(),
                conversation.getModelId(), splitIds(conversation.getChannelIds()),
                format(conversation.getCreateTime()), format(conversation.getUpdateTime()),
                Boolean.TRUE.equals(conversation.getPinned()), favorites.contains(conversation.getId()),
                Boolean.TRUE.equals(conversation.getArchived()));
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

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,3})\\]");

    private List<CitationSource> usedCitations(String text, List<CitationSource> sources) {
        if (sources.isEmpty() || !StringUtils.hasText(text)) {
            return List.of();
        }
        Set<Integer> used = new HashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(text);
        while (matcher.find()) {
            used.add(Integer.parseInt(matcher.group(1)));
        }
        if (used.isEmpty()) {
            return List.of();
        }
        return sources.stream().filter(source -> used.contains(source.index())).toList();
    }

    private List<CitationSource> parseCitations(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            return JSON.convertValue(node, JSON.getTypeFactory()
                    .constructCollectionType(List.class, CitationSource.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private java.util.List<ChatAttachmentRequest> parseAttachments(String json) {
        if (!StringUtils.hasText(json)) {
            return java.util.List.of();
        }
        try {
            java.util.List<ChatAttachmentRequest> parsed = JSON.readValue(json,
                    new TypeReference<java.util.List<ChatAttachmentRequest>>() {
                    });
            return parsed.stream().map(attachment -> {
                if (attachment == null || !StringUtils.hasText(attachment.key())
                        || StringUtils.hasText(attachment.url())) {
                    return attachment;
                }
                String url = null;
                try {
                    url = objectStorageService.presignedUrl(attachment.key(), 3600).url();
                } catch (Exception ex) {
                    log.debug("Failed to presign chat attachment {}", attachment.key());
                }
                return new ChatAttachmentRequest(attachment.type(), attachment.name(), attachment.mimeType(),
                        null, attachment.text(), url, attachment.key());
            }).toList();
        } catch (Exception ex) {
            log.warn("Failed to parse attachments", ex);
            return java.util.List.of();
        }
    }
}
