package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFolderRequest(
        Long parentId,
        @NotBlank(message = "文件夹名称不能为空")
        @Size(max = 128, message = "文件夹名称不能超过 128 个字符") String name
) {
}
