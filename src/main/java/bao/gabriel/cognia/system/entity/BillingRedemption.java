package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_billing_redemption")
public class BillingRedemption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long codeId;
    private String idempotencyKey;
    private String benefitType;
    private String benefitSnapshot;
    private LocalDateTime redeemedAt;
}
