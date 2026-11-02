package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTeamMemberRoleRequest(
        @NotBlank(message = "请选择成员角色")
        @Pattern(regexp = "^(MANAGER|EDITOR|MEMBER)$", message = "成员角色只能为 MANAGER、EDITOR 或 MEMBER")
        String role
) {
}
