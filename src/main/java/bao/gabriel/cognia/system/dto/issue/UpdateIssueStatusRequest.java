package bao.gabriel.cognia.system.dto.issue;

import jakarta.validation.constraints.NotBlank;

public record UpdateIssueStatusRequest(@NotBlank String status) {
}
