package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotNull;

public record PinChannelRequest(
        @NotNull(message = "请指定置顶状态")
        Boolean pinned
) {
}
