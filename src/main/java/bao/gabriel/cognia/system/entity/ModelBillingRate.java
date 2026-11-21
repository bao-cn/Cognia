package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_model_billing_rate")
public class ModelBillingRate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelConfigId;
    private Long inputCreditsPerMillion;
    private Long outputCreditsPerMillion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
