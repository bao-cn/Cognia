package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.feedback.FeedbackDetailResponse;
import bao.gabriel.cognia.system.dto.feedback.FeedbackResponse;
import bao.gabriel.cognia.system.dto.feedback.SubmitFeedbackRequest;
import bao.gabriel.cognia.system.dto.feedback.UpdateFeedbackRequest;
import bao.gabriel.cognia.system.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/chat/messages/{messageId}/feedback")
    public ApiResponse<FeedbackResponse> submit(@PathVariable Long messageId,
                                                @Valid @RequestBody SubmitFeedbackRequest request) {
        return ApiResponse.success(feedbackService.submit(
                SecurityUtils.getUserId(), messageId, request.type(), request.reason()));
    }

    @GetMapping("/admin/feedback")
    @PreAuthorize("hasAnyAuthority('feedback:read', 'settings:read')")
    public ApiResponse<PageResult<FeedbackResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "20") long size,
                                                         @RequestParam(required = false) String type,
                                                         @RequestParam(required = false) String status) {
        return ApiResponse.success(feedbackService.page(page, size, type, status));
    }

    @GetMapping("/admin/feedback/{id}")
    @PreAuthorize("hasAnyAuthority('feedback:read', 'settings:read')")
    public ApiResponse<FeedbackDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(feedbackService.detail(id));
    }

    @PutMapping("/admin/feedback/{id}")
    @PreAuthorize("hasAnyAuthority('feedback:update', 'settings:update')")
    public ApiResponse<FeedbackResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateFeedbackRequest request) {
        return ApiResponse.success(feedbackService.update(id, request.problemType(), request.status(), request.note()));
    }
}
