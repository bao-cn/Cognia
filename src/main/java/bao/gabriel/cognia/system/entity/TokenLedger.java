package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_token_ledger")
public class TokenLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private Long deltaTokens;
    private Long balanceAfter;
    private String sourceType;
    private Long sourceId;
    private String requestId;
    private LocalDateTime createdAt;
}
