package bao.gabriel.cognia.system.dto.issue;

import java.util.List;

public record IssueSummaryResponse(
        Long id,
        Long number,
        String title,
        String status,
        IssueUserResponse author,
        IssueUserResponse assignee,
        List<IssueLabelResponse> labels,
        long commentCount,
        String createTime,
        String updateTime
) {
}
