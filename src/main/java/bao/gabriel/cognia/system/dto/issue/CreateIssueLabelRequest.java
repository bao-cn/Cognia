package bao.gabriel.cognia.system.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIssueLabelRequest(
        @NotBlank @Size(max = 32) String name,
        @NotBlank String color
) {
}
