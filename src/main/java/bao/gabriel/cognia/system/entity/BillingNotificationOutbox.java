package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_billing_notification_outbox")
public class BillingNotificationOutbox {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventKey;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String payloadJson;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptTime;
    private LocalDateTime processedTime;
    private String lastError;
    private LocalDateTime createTime;
}
