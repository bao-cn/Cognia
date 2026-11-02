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
@TableName("sys_model_config")
public class ModelConfig {

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_EMBEDDING = "EMBEDDING";
    public static final String TYPE_RERANK = "RERANK";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private String modelId;
    private String displayName;
    private String modelType;
    private Boolean enabled;
    private Boolean visible;
    private Boolean supportsTools;
    private Boolean thinkingEnabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reasoningEffort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String extraBody;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer dimension;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean visible() {
        return Boolean.TRUE.equals(visible);
    }

    public boolean supportsTools() {
        return Boolean.TRUE.equals(supportsTools);
    }

    public boolean thinkingEnabled() {
        return Boolean.TRUE.equals(thinkingEnabled);
    }
}
