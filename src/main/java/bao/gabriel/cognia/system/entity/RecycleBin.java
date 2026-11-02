package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Logical-delete entry. Cleanup tasks use both object locations and deleteTime
 * to remove the S3 object, document row, pgvector embeddings and this row.
 */
@Data
@NoArgsConstructor
@TableName("kb_recycle_bin")
public class RecycleBin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private String ownerType;
    private Long ownerId;
    private Long channelId;
    private String objectKey;
    private String originalObjectKey;
    private String name;
    private Long size;
    private String contentType;
    private Long deletedBy;
    private Integer retentionDays;
    private LocalDateTime deleteTime;
    private LocalDateTime createTime;
}
