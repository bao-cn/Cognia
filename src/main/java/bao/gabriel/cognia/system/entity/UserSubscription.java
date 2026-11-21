package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_user_subscription")
public class UserSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long planId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime nextQuotaTime;
    private String productSnapshot;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
