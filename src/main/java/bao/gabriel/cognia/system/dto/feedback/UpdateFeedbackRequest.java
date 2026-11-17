package bao.gabriel.cognia.system.dto.feedback;

import jakarta.validation.constraints.Size;

public record UpdateFeedbackRequest(
        @Size(max = 64) String problemType,
        @Size(max = 16) String status,
        @Size(max = 2000) String note
) {}
