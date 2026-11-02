package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotNull;

public record TransferTeamRequest(
        @NotNull(message = "请选择新的团队创建者")
        Long userId
) {
}
