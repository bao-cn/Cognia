package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.rag.service.ChatService;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.chat.ChatRequest;
import bao.gabriel.cognia.system.dto.chat.ForkConversationRequest;
import bao.gabriel.cognia.system.dto.chat.PublicChatModel;
import bao.gabriel.cognia.system.dto.model.PublicModelResponse;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.RagSettingService;
import bao.gabriel.cognia.system.service.BillingService;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.api.ResultCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ModelConfigService modelConfigService;
    private final ChatService chatService;
    private final ChatConversationService conversationService;
    private final RagSettingService ragSettingService;
    private final BillingService billingService;

    @GetMapping("/capabilities")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> capabilities() {
        boolean enabled = ragSettingService.webSearchEnabled();
        return ApiResponse.success(Map.of(
                "webSearchEnabled", enabled,
                "allowUserWebSearch", ragSettingService.allowUserWebSearch(),
                "webSearchAvailable", enabled));
    }

    @GetMapping("/models")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PublicChatModel>> models() {
        java.util.Map<String, Object> me = billingService.me(SecurityUtils.getUserId());
        java.util.Set<String> allowed = ((java.util.List<java.util.Map<String, Object>>) me.getOrDefault("allowedModels", java.util.List.of()))
                .stream().map(m -> String.valueOf(m.get("id"))).collect(java.util.stream.Collectors.toSet());
        return ApiResponse.success(modelConfigService.publicModels().stream()
                .filter(model -> allowed.isEmpty() || allowed.contains(String.valueOf(model.id())))
                .map(this::toChatModel).toList());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('chat:use')")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        String requestId = java.util.UUID.randomUUID().toString();
        ChatRequest effectiveRequest = request;
        if (request.modelId() == null) {
            java.util.Map<String, Object> me = billingService.me(SecurityUtils.getUserId());
            java.util.List<java.util.Map<String, Object>> allowedModels =
                    (java.util.List<java.util.Map<String, Object>>) me.getOrDefault("allowedModels", java.util.List.of());
            if (allowedModels.isEmpty()) throw new BusinessException(ResultCode.MODEL_NOT_ENTITLED);
            Long allowedModelId;
            try {
                allowedModelId = Long.valueOf(String.valueOf(allowedModels.getFirst().get("id")));
            } catch (Exception ex) { throw new BusinessException(ResultCode.MODEL_NOT_ENTITLED); }
            effectiveRequest = new ChatRequest(allowedModelId, request.messages(), request.channelIds(), request.effort(),
                    request.thinking(), request.conversationId(), request.webSearch(), request.temporary(),
                    request.attachments(), request.regenerate(), request.editMessageIndex(), request.debug(),
                    request.debugTopK(), request.debugRerankEnabled(), request.debugToolsEnabled());
        }
        final ChatRequest finalRequest = effectiveRequest;
        if (finalRequest.modelId() != null) {
            java.util.Map<String, Object> me = billingService.me(SecurityUtils.getUserId());
            java.util.List<java.util.Map<String, Object>> allowedModels =
                    (java.util.List<java.util.Map<String, Object>>) me.getOrDefault("allowedModels", java.util.List.of());
            boolean allowed = allowedModels.stream()
                    .anyMatch(model -> String.valueOf(model.get("id")).equals(String.valueOf(finalRequest.modelId())));
            if (!allowed) throw new BusinessException(ResultCode.MODEL_NOT_ENTITLED);
        }
        var model = modelConfigService.resolveChat(finalRequest.modelId());
        long inputEstimate = finalRequest.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .mapToLong(message -> message.content() == null ? 0L
                        : Math.max(1L, message.content().length() / 4L))
                .sum();
        billingService.reserveChatTokens(SecurityUtils.getUserId(), requestId, model.id(),
                inputEstimate, model.maxOutputTokensOrDefault());
        return chatService.stream(finalRequest, SecurityUtils.getUserId(), requestId);
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('chat:read')")
    public ApiResponse<List<bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse>> conversations(
            @RequestParam(defaultValue = "false") boolean archived) {
        return ApiResponse.success(conversationService.list(SecurityUtils.getUserId(), archived));
    }

    @GetMapping("/conversations/{id}")
    @PreAuthorize("hasAuthority('chat:read')")
    public ApiResponse<bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse> conversation(
            @PathVariable String id) {
        return ApiResponse.success(conversationService.detail(id, SecurityUtils.getUserId()));
    }

    @PostMapping("/conversations/{id}/fork")
    @PreAuthorize("hasAuthority('chat:use')")
    public ApiResponse<bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse> forkConversation(
            @PathVariable String id, @Valid @RequestBody ForkConversationRequest request) {
        return ApiResponse.success(conversationService.fork(id, SecurityUtils.getUserId(), request.messageIndex()));
    }

    @PutMapping("/conversations/{id}/pin")
    @PreAuthorize("hasAuthority('chat:read')")
    public ApiResponse<Void> pinConversation(@PathVariable String id,
                                             @Valid @RequestBody bao.gabriel.cognia.system.dto.chat.PinConversationRequest request) {
        conversationService.setPinned(id, SecurityUtils.getUserId(), Boolean.TRUE.equals(request.pinned()));
        return ApiResponse.success();
    }

    @PutMapping("/conversations/{id}/archive")
    @PreAuthorize("hasAuthority('chat:read')")
    public ApiResponse<Void> archiveConversation(@PathVariable String id,
                                                 @RequestBody(required = false) bao.gabriel.cognia.system.dto.chat.ArchiveConversationRequest request) {
        boolean archived = request == null || request.archived() == null || request.archived();
        conversationService.archive(id, SecurityUtils.getUserId(), archived);
        return ApiResponse.success();
    }

    private PublicChatModel toChatModel(PublicModelResponse model) {
        return new PublicChatModel(model.id(), model.providerName(), model.modelId(),
                model.displayName(), model.supportsTools(), model.thinkingEnabled(), model.supportsVision());
    }
}
