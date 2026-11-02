package bao.gabriel.cognia.system.dto.downloads;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateDownloadAssetRequest(
        @NotBlank(message = "下载名称不能为空")
        @Size(max = 64, message = "下载名称不能超过 64 个字符")
        String name,

        @NotBlank(message = "版本号不能为空")
        @Size(max = 32, message = "版本号不能超过 32 个字符")
        String version,

        @Size(max = 1024, message = "下载链接不能超过 1024 个字符")
        String downloadUrl,

        @Pattern(regexp = "^$|^[a-fA-F0-9]{64}$", message = "SHA256 必须是 64 位十六进制字符串")
        String sha256,

        @PositiveOrZero(message = "文件大小不能为负数")
        Long fileSize,

        Boolean enabled
) {
}
