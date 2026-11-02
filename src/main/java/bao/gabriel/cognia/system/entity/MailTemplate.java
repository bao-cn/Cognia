package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Editable plain-text mail template. Placeholders use the {@code {{name}}}
 * syntax and are replaced when the mail is sent.
 */
@Data
@NoArgsConstructor
@TableName("sys_mail_template")
public class MailTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    private String subject;

    private String content;

    private Long updatedBy;

    private LocalDateTime updatedAt;

    private LocalDateTime createTime;
}
