package bao.gabriel.cognia.system.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIssueCommentRequest(
        @NotBlank @Size(max = 20000) String body,
        Long quoteCommentId
) {
}
