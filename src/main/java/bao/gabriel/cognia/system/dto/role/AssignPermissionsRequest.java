package bao.gabriel.cognia.system.dto.role;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignPermissionsRequest(
        @NotNull(message = "permissionIds 不能为空")
        List<Long> permissionIds
) {
}
