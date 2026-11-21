package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_wallet_account")
public class WalletAccount {
    @TableId
    private Long userId;
    private Long balanceUnits;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
