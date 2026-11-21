package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.issue.*;
import bao.gabriel.cognia.system.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/channels/{channelId}/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    public ApiResponse<PageResult<IssueSummaryResponse>> page(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) Long labelId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        String filter = "ALL".equalsIgnoreCase(status) ? null : status;
        return ApiResponse.success(issueService.page(channelId, SecurityUtils.getUserId(), filter, labelId, page, size));
    }

    @PostMapping
    public ApiResponse<IssueDetailResponse> create(@PathVariable Long channelId,
                                                   @Valid @RequestBody CreateIssueRequest request) {
        return ApiResponse.success(issueService.create(channelId, SecurityUtils.getUserId(), request));
    }

    @GetMapping("/{number}")
    public ApiResponse<IssueDetailResponse> detail(@PathVariable Long channelId, @PathVariable Long number) {
        return ApiResponse.success(issueService.detail(channelId, number, SecurityUtils.getUserId()));
    }

    @PutMapping("/{number}/status")
    public ApiResponse<IssueDetailResponse> status(@PathVariable Long channelId, @PathVariable Long number,
                                                   @Valid @RequestBody UpdateIssueStatusRequest request) {
        return ApiResponse.success(issueService.updateStatus(channelId, number,
                SecurityUtils.getUserId(), request.status()));
    }

    @PutMapping("/{number}/assignee")
    public ApiResponse<IssueDetailResponse> assignee(@PathVariable Long channelId, @PathVariable Long number,
                                                     @RequestBody UpdateIssueAssigneeRequest request) {
        return ApiResponse.success(issueService.updateAssignee(channelId, number,
                SecurityUtils.getUserId(), request.assigneeId()));
    }

    @PutMapping("/{number}/labels")
    public ApiResponse<IssueDetailResponse> labels(@PathVariable Long channelId, @PathVariable Long number,
                                                   @RequestBody UpdateIssueLabelsRequest request) {
        return ApiResponse.success(issueService.updateLabels(channelId, number,
                SecurityUtils.getUserId(), request.labelIds()));
    }

    @PostMapping("/{number}/comments")
    public ApiResponse<IssueCommentResponse> comment(@PathVariable Long channelId, @PathVariable Long number,
                                                     @Valid @RequestBody CreateIssueCommentRequest request) {
        return ApiResponse.success(issueService.comment(channelId, number,
                SecurityUtils.getUserId(), request));
    }

    @GetMapping("/labels")
    public ApiResponse<List<IssueLabelResponse>> labels(@PathVariable Long channelId) {
        return ApiResponse.success(issueService.labels(channelId, SecurityUtils.getUserId()));
    }

    @PostMapping("/labels")
    public ApiResponse<IssueLabelResponse> createLabel(@PathVariable Long channelId,
                                                       @Valid @RequestBody CreateIssueLabelRequest request) {
        return ApiResponse.success(issueService.createLabel(channelId, SecurityUtils.getUserId(), request));
    }

    @DeleteMapping("/labels/{labelId}")
    public ApiResponse<Void> deleteLabel(@PathVariable Long channelId, @PathVariable Long labelId) {
        issueService.deleteLabel(channelId, SecurityUtils.getUserId(), labelId);
        return ApiResponse.success();
    }

    @GetMapping("/assignees")
    public ApiResponse<List<IssueUserResponse>> assignees(@PathVariable Long channelId) {
        return ApiResponse.success(issueService.assignees(channelId, SecurityUtils.getUserId()));
    }
}
