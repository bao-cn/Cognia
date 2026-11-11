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
    private Boolean supportsVision;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reasoningEffort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String extraBody;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer dimension;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private Boolean supportsText;
    /** JSON array of {"label":"low","value":"low"} reasoning presets. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reasoningLevels;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static final int DEFAULT_CONTEXT_WINDOW = 32000;
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;

    public int contextWindow() {
        return contextWindow == null || contextWindow < 1000 ? DEFAULT_CONTEXT_WINDOW : contextWindow;
    }

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

    public boolean supportsVision() {
        return Boolean.TRUE.equals(supportsVision);
    }

    public boolean supportsText() {
        return !Boolean.FALSE.equals(supportsText);
    }

    public int maxOutputTokensOrDefault() {
        return maxOutputTokens == null || maxOutputTokens < 1 ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    }
}
