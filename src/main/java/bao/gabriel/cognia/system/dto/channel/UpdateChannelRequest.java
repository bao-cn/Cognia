package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChannelRequest(
        @NotBlank(message = "频道名称不能为空")
        @Size(max = 64, message = "频道名称不能超过 64 个字符") String name,
        @Size(max = 512, message = "频道简介不能超过 512 个字符") String description,
        Boolean isPublic,
        Boolean webSearchEnabled
) {
}
