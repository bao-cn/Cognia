package bao.gabriel.cognia.system.dto.issue;

public record IssueCommentResponse(
        Long id,
        IssueUserResponse author,
        String body,
        Long quoteCommentId,
        IssueUserResponse quoteAuthor,
        String quoteBody,
        String createTime
) {
}
