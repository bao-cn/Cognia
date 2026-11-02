package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notification row. Online delivery happens over SSE; the row is persisted so
 * offline users can catch up (delivered = FALSE) and everyone keeps history.
 */
@Data
@NoArgsConstructor
@TableName("sys_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String type;
    private String title;
    private String content;
    private String payloadJson;
    private Boolean readFlag;
    private Boolean delivered;
    private LocalDateTime readTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime createTime;

    public boolean isRead() {
        return Boolean.TRUE.equals(readFlag);
    }
}
