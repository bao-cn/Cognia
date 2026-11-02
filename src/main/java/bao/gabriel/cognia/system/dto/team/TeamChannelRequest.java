package bao.gabriel.cognia.system.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamChannelRequest(
        @NotBlank(message = "频道名称不能为空")
        @Size(min = 2, max = 64, message = "频道名称长度需为 2-64 个字符")
        String name,

        @Size(max = 512, message = "频道介绍不能超过 512 个字符")
        String description,

        Boolean isPublic
) {
}
