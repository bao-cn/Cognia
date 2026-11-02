package bao.gabriel.cognia.system.dto.user;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignRolesRequest(
        @NotNull(message = "roleIds 不能为空")
        List<Long> roleIds
) {
}
