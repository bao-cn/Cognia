package bao.gabriel.cognia.system.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
        @NotBlank String type,
        @Size(max = 500) String reason
) {}
