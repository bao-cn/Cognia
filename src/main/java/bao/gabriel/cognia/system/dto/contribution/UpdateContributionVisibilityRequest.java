package bao.gabriel.cognia.system.dto.contribution;

import jakarta.validation.constraints.NotNull;

public record UpdateContributionVisibilityRequest(@NotNull Boolean publicVisible) {
}
