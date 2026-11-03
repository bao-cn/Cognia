package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.channel.*;
import bao.gabriel.cognia.system.service.ChannelNotificationService;
import bao.gabriel.cognia.system.service.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelNotificationService channelNotificationService;

    @GetMapping("/owner/{ownerType}/{ownerId}")
    public ApiResponse<List<ChannelSummaryResponse>> list(@PathVariable String ownerType,
                                                          @PathVariable Long ownerId) {
        Long viewerId = SecurityUtils.getLoginUserOrNull() == null ? null : SecurityUtils.getUserId();
        return ApiResponse.success(channelService.list(ownerType, ownerId, viewerId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChannelDetailResponse> detail(@PathVariable Long id) {
        Long viewerId = SecurityUtils.getLoginUserOrNull() == null ? null : SecurityUtils.getUserId();
        return ApiResponse.success(channelService.detail(id, viewerId));
    }

    @PostMapping
    public ApiResponse<ChannelSummaryResponse> create(@Valid @RequestBody CreateChannelRequest request) {
        return ApiResponse.success(channelService.create(SecurityUtils.getUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChannelSummaryResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateChannelRequest request) {
        return ApiResponse.success(channelService.update(SecurityUtils.getUserId(), id, request));
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<ChannelSummaryResponse> setPinned(@PathVariable Long id, @RequestParam boolean pinned) {
        return ApiResponse.success(channelService.setPinned(SecurityUtils.getUserId(), id, pinned));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        channelService.delete(SecurityUtils.getUserId(), id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/folders")
    public ApiResponse<FolderResponse> createFolder(@PathVariable Long id,
                                                    @Valid @RequestBody CreateFolderRequest request) {
        return ApiResponse.success(channelService.createFolder(SecurityUtils.getUserId(), id, request));
    }

    @DeleteMapping("/{id}/folders/{folderId}")
    public ApiResponse<Void> deleteFolder(@PathVariable Long id, @PathVariable Long folderId) {
        channelService.deleteFolder(SecurityUtils.getUserId(), id, folderId);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/documents")
    public ApiResponse<List<ChannelDocumentResponse>> documents(@PathVariable Long id,
                                                               @RequestParam(required = false) Long folderId) {
        Long viewerId = SecurityUtils.getLoginUserOrNull() == null ? null : SecurityUtils.getUserId();
        return ApiResponse.success(channelService.documents(id, viewerId, folderId));
    }

    @PostMapping("/{id}/documents")
    public ApiResponse<ChannelDocumentResponse> upload(@PathVariable Long id,
                                                       @RequestParam(required = false) Long folderId,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) String title,
                                                       @RequestParam(required = false) String documentType,
                                                       @RequestParam(required = false) String expirationTime) {
        return ApiResponse.success(channelService.uploadDocument(
                SecurityUtils.getUserId(), id, folderId, file, title, documentType, expirationTime));
    }

    @PostMapping("/{id}/markdown")
    public ApiResponse<ChannelDocumentResponse> uploadMarkdown(@PathVariable Long id,
                                                              @RequestParam String kind,
                                                              @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(channelService.uploadMarkdown(SecurityUtils.getUserId(), id, kind, file));
    }

    @GetMapping("/{id}/markdown/{kind}")
    public ApiResponse<String> markdown(@PathVariable Long id, @PathVariable String kind) {
        Long viewerId = SecurityUtils.getLoginUserOrNull() == null ? null : SecurityUtils.getUserId();
        return ApiResponse.success(channelService.readMarkdown(id, viewerId, kind));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        channelService.deleteDocument(SecurityUtils.getUserId(), id, documentId);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/documents/{documentId}/retry")
    public ApiResponse<Void> retry(@PathVariable Long id, @PathVariable Long documentId) {
        channelService.retryDocument(SecurityUtils.getUserId(), id, documentId);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/agent")
    public ApiResponse<ChannelSummaryResponse> updateAgent(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateAgentMetaRequest request) {
        return ApiResponse.success(channelService.updateAgentMeta(SecurityUtils.getUserId(), id, request));
    }

    @PutMapping("/{id}/web-search")
    public ApiResponse<ChannelSummaryResponse> setWebSearch(@PathVariable Long id,
                                                            @RequestParam boolean enabled) {
        return ApiResponse.success(channelService.setWebSearchEnabled(SecurityUtils.getUserId(), id, enabled));
    }

    @PostMapping("/{id}/notifications")
    public ApiResponse<ChannelNotificationResponse> sendNotification(
            @PathVariable Long id,
            @Valid @RequestBody ChannelNotificationRequest request) {
        return ApiResponse.success(channelNotificationService.send(id, SecurityUtils.getUserId(), request));
    }
}
