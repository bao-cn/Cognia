package bao.gabriel.cognia.system.dto.issue;

import java.util.List;

public record UpdateIssueLabelsRequest(List<Long> labelIds) {
}
