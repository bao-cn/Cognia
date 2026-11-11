package bao.gabriel.cognia.rag.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.rag.service.ChatAttachmentService;
import bao.gabriel.cognia.system.dto.chat.ChatAttachmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatAttachmentController {

    private final ChatAttachmentService chatAttachmentService;

    @PostMapping("/attachments")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ChatAttachmentResponse> upload(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "conversationId", required = false) String conversationId) {
        return ApiResponse.success(chatAttachmentService.prepare(
                bao.gabriel.cognia.security.SecurityUtils.getUserId(), conversationId, file));
    }
}
