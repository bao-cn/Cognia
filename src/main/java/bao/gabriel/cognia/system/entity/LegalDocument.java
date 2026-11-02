package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Versioned privacy policy / terms of service. A content change increments
 * {@link #version}, invalidating all previous consents.
 */
@Data
@NoArgsConstructor
@TableName("sys_legal_document")
public class LegalDocument {

    public static final String TYPE_PRIVACY = "PRIVACY";
    public static final String TYPE_TERMS = "TERMS";
    public static final String TYPE_OPEN_SOURCE = "OPEN_SOURCE";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String docType;

    private String title;

    private String content;

    private Integer version;

    private LocalDateTime publishedAt;

    private Long updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
