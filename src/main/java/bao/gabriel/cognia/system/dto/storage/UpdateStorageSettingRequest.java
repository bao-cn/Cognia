package bao.gabriel.cognia.system.dto.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update from the console. Omitted values keep their stored value;
 * empty strings are explicit clears. The secret key is only replaced when a
 * non-blank value is supplied or {@code clearSecretKey} is true.
 */
public record UpdateStorageSettingRequest(
        Boolean enabled,

        @Size(max = 512, message = "S3 Endpoint 长度不能超过 512 个字符")
        String endpoint,

        @Size(max = 64, message = "区域长度不能超过 64 个字符")
        String region,

        @Size(max = 128, message = "Bucket 长度不能超过 128 个字符")
        String bucket,

        @Size(max = 255, message = "AccessKey 长度不能超过 255 个字符")
        String accessKey,

        @Size(max = 1024, message = "SecretKey 长度不能超过 1024 个字符")
        String secretKey,

        Boolean clearSecretKey,

        Boolean pathStyleAccess,

        @Size(max = 512, message = "公共访问地址长度不能超过 512 个字符")
        String publicBaseUrl,

        @Size(max = 255, message = "对象前缀长度不能超过 255 个字符")
        String objectPrefix,

        @Min(value = 1, message = "单文件大小上限最小为 1 MB")
        @Max(value = 512, message = "单文件大小上限最大为 512 MB")
        Integer maxFileSizeMb,

        @Min(value = 60, message = "预签名有效期最小为 60 秒")
        @Max(value = 604800, message = "预签名有效期最大为 604800 秒")
        Integer presignedUrlExpirySeconds,

        @Min(value = 1, message = "临时文件保留天数最小为 1 天")
        @Max(value = 365, message = "临时文件保留天数最大为 365 天")
        Integer tempRetentionDays
) {
}
