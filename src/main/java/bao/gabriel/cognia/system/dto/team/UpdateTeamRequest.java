package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateTeamRequest(
        @NotBlank(message = "团队名称不能为空")
        @Size(min = 2, max = 64, message = "团队名称长度需为 2-64 个字符")
        String name,

        @Size(max = 512, message = "团队介绍不能超过 512 个字符")
        String description,

        @Size(max = 200000, message = "团队 Logo 数据过大")
        String logoUrl,

        @Size(max = 4, message = "自定义链接最多 4 个")
        List<@Valid TeamLinkRequest> links,

        Boolean joinRequestsEnabled,

        @Pattern(regexp = "^(MANAGER|EDITOR|MEMBER)$", message = "默认成员权限只能为 MANAGER、EDITOR 或 MEMBER")
        String defaultMemberRole
) {
}
