package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotNull;

public record ChannelArchiveRequest(@NotNull Boolean archived, String verificationCode) {
}
