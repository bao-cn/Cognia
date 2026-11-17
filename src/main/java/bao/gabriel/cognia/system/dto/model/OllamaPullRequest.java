package bao.gabriel.cognia.system.dto.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OllamaPullRequest(
        Long providerId,
        @NotBlank @Size(max = 128) String model,
        Boolean insecure
) {
}
