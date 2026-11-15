package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotNull;

public record ChannelVisibilityRequest(@NotNull Boolean isPublic, String verificationCode) {
}
