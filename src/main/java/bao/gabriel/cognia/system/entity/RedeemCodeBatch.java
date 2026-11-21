package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_billing_redeem_code_batch")
public class RedeemCodeBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String benefitType;
    private Long productId;
    private String benefitSnapshot;
    private Integer quantity;
    private Long createdBy;
    private LocalDateTime validFrom;
    private LocalDateTime expireTime;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
