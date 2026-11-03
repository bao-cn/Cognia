package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.rag.service.ChatService;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.chat.ChatRequest;
import bao.gabriel.cognia.system.dto.chat.PublicChatModel;
import bao.gabriel.cognia.system.dto.model.PublicModelResponse;
import bao.gabriel.cognia.system.service.ChatConversationService;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.RagSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        return ApiResponse.success(modelConfigService.publicModels().stream()
                .map(this::toChatModel).toList());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request, SecurityUtils.getUserId());
    }

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<bao.gabriel.cognia.system.dto.chat.ConversationSummaryResponse>> conversations() {
        return ApiResponse.success(conversationService.list(SecurityUtils.getUserId()));
    }

    @GetMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<bao.gabriel.cognia.system.dto.chat.ConversationDetailResponse> conversation(
            @PathVariable String id) {
        return ApiResponse.success(conversationService.detail(id, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> deleteConversation(@PathVariable String id) {
        conversationService.delete(id, SecurityUtils.getUserId());
        return ApiResponse.success();
    }

    private PublicChatModel toChatModel(PublicModelResponse model) {
        return new PublicChatModel(model.id(), model.providerName(), model.modelId(),
                model.displayName(), model.supportsTools(), model.thinkingEnabled(), model.supportsVision());
    }
}
