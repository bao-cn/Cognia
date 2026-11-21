package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_payment_webhook_event")
public class PaymentWebhookEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String providerEventId;
    private String payloadHash;
    private LocalDateTime processedTime;
    private String result;
    private LocalDateTime createTime;
}
