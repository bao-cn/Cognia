package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.issue.*;

import java.util.List;

public interface IssueService {
    PageResult<IssueSummaryResponse> page(Long channelId, Long viewerId, String status, Long labelId, long page, long size);

    IssueDetailResponse detail(Long channelId, Long number, Long viewerId);

    IssueDetailResponse create(Long channelId, Long userId, CreateIssueRequest request);

    IssueDetailResponse updateStatus(Long channelId, Long number, Long userId, String status);

    IssueDetailResponse updateAssignee(Long channelId, Long number, Long userId, Long assigneeId);

    IssueDetailResponse updateLabels(Long channelId, Long number, Long userId, List<Long> labelIds);

    IssueCommentResponse comment(Long channelId, Long number, Long userId, CreateIssueCommentRequest request);

    List<IssueLabelResponse> labels(Long channelId, Long viewerId);

    IssueLabelResponse createLabel(Long channelId, Long userId, CreateIssueLabelRequest request);

    void deleteLabel(Long channelId, Long userId, Long labelId);

    List<IssueUserResponse> assignees(Long channelId, Long viewerId);
}
