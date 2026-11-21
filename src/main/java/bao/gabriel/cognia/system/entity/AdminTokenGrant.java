package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_admin_token_grant")
public class AdminTokenGrant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String grantNo;
    private Long userId;
    private Long tokenPackId;
    private Long operatorId;
    private Long tokens;
    private String reason;
    private LocalDateTime expireTime;
    private String idempotencyKey;
    private Long ledgerId;
    private LocalDateTime createTime;
}
