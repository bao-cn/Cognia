package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotBlank(message = "团队名称不能为空")
        @Size(min = 2, max = 64, message = "团队名称长度需为 2-64 个字符")
        String name,

        @NotBlank(message = "团队标识不能为空")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "团队标识只能包含小写字母、数字和单个短横线，且不能以短横线开头或结尾")
        @Size(min = 2, max = 63, message = "团队标识长度需为 2-63 个字符")
        String slug,

        @Size(max = 512, message = "团队介绍不能超过 512 个字符")
        String description,

        @Size(max = 512, message = "团队 Logo 地址过长")
        String logoUrl
) {
}
