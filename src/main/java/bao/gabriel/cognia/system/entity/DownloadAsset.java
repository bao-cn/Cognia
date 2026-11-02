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
 * A downloadable desktop build. The binary itself is hosted externally; the
 * admin console stores its version, URL and checksum.
 */
@Data
@NoArgsConstructor
@TableName("sys_download_asset")
public class DownloadAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String platform;

    private String arch;

    private String packageType;

    private String version;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String downloadUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sha256;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fileSize;

    private Integer sort;

    private Boolean enabled;

    private Long updatedBy;

    private LocalDateTime updatedAt;

    private LocalDateTime createTime;
}
