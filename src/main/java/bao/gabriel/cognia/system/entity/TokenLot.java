package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_token_lot")
public class TokenLot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sourceType;
    private Long sourceId;
    private Long grantedTokens;
    private Long remainingTokens;
    private Long heldTokens;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
