package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_model_provider")
public class ModelProvider {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String vendor;
    private String baseUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String apiKey;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long updatedBy;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
