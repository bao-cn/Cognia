package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
        @NotBlank(message = "请选择频道归属") String ownerType,
        Long ownerId,
        @NotBlank(message = "频道名称不能为空")
        @Size(max = 64, message = "频道名称不能超过 64 个字符") String name,
        @NotBlank(message = "频道标识不能为空")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "频道标识只能包含小写字母、数字和单个短横线")
        @Size(min = 2, max = 63, message = "频道标识长度需为 2-63 个字符") String slug,
        @Size(max = 512, message = "频道简介不能超过 512 个字符") String description,
        Boolean isPublic
) {
}
