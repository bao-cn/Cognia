package bao.gabriel.cognia.system.dto.issue;

import java.util.List;

public record IssueDetailResponse(
        Long id,
        Long number,
        String title,
        String body,
        String status,
        IssueUserResponse author,
        IssueUserResponse assignee,
        List<IssueLabelResponse> labels,
        long commentCount,
        String createTime,
        String updateTime,
        boolean canManage,
        boolean canClose,
        List<IssueCommentResponse> comments
) {
}
