package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_wallet_ledger")
public class WalletLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private Long deltaUnits;
    private Long balanceAfterUnits;
    private String currency;
    private Integer fractionalDigits;
    private String unitLabel;
    private String sourceType;
    private Long sourceId;
    private String requestId;
    private String reason;
    private LocalDateTime createdAt;
}
