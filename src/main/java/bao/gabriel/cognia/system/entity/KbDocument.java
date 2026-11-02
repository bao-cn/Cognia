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
 * Knowledge base document metadata. The status transitions are enforced by
 * {@link bao.gabriel.cognia.system.service.DocumentStateMachine}.
 */
@Data
@NoArgsConstructor
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ownerType;
    private Long ownerId;
    private Long channelId;
    private Long folderId;
    private String name;
    private String title;
    private String author;
    private String documentType;
    private java.time.LocalDateTime expirationTime;
    private Integer version;
    private String objectKey;
    private String originalKey;
    private Long size;
    private String contentType;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    private Integer retryCount;
    private Long createdBy;
    private LocalDateTime processedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
