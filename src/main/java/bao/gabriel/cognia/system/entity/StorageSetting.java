package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single-row S3-compatible object storage configuration (id = 1). The secret
 * access key is persisted as an AES-GCM ciphertext.
 */
@Data
@NoArgsConstructor
@TableName("sys_storage_setting")
public class StorageSetting {

    public static final long SINGLETON_ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;

    private Boolean enabled;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String endpoint;

    private String region;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bucket;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String accessKey;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secretKey;

    private Boolean pathStyleAccess;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String publicBaseUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String objectPrefix;

    private Integer maxFileSizeMb;

    private Integer presignedUrlExpirySeconds;

    private Integer tempRetentionDays;

    private Long updatedBy;

    private LocalDateTime updatedAt;

    private LocalDateTime createTime;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean pathStyleAccess() {
        return Boolean.TRUE.equals(pathStyleAccess);
    }

    public int maxFileSizeMb() {
        return maxFileSizeMb == null || maxFileSizeMb < 1 ? 100 : maxFileSizeMb;
    }

    public int presignedUrlExpirySeconds() {
        return presignedUrlExpirySeconds == null || presignedUrlExpirySeconds < 60
                ? 3600 : presignedUrlExpirySeconds;
    }

    public int tempRetentionDays() {
        return tempRetentionDays == null || tempRetentionDays < 1 ? 7 : tempRetentionDays;
    }
}
