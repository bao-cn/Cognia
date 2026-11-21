package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_billing_redeem_code")
public class RedeemCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String codeHash;
    private String status;
    private Long redeemedBy;
    private LocalDateTime redeemedAt;
    private LocalDateTime createTime;
}
