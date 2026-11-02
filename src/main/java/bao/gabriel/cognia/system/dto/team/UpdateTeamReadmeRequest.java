package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.Size;

public record UpdateTeamReadmeRequest(
        @Size(max = 20000, message = "团队 README 不能超过 20000 个字符")
        String readme
) {
}
