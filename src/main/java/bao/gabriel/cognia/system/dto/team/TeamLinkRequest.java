package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamLinkRequest(
        @NotBlank(message = "链接名称不能为空")
        @Size(max = 24, message = "链接名称不能超过 24 个字符")
        String label,

        @NotBlank(message = "链接地址不能为空")
        @Size(max = 512, message = "链接地址过长")
        String url
) {
}
