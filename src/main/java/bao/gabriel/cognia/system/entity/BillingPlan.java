package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_billing_plan")
public class BillingPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String planType;
    private Long priceMinor;
    private String currency;
    private Integer durationDays;
    private Long quotaTokens;
    private Integer quotaCycleDays;
    private String status;
    private Boolean isDefault;
    private Integer version;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
