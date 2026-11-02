package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotNull;

public record InviteTeamMemberRequest(
        @NotNull(message = "请选择要邀请的用户")
        Long userId
) {
}
