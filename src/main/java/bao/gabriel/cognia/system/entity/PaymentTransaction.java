package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_payment_transaction")
public class PaymentTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String provider;
    private String providerTransactionId;
    private Long amountMinor;
    private String currency;
    private String status;
    private LocalDateTime paidTime;
    private String rawPayload;
    private LocalDateTime createTime;
}
