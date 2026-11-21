package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_billing_plan_model")
public class BillingPlanModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long modelConfigId;
    private LocalDateTime createTime;
}
