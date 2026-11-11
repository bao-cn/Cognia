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

    public static final String PROTOCOL_OPENAI_CHAT = "OPENAI_CHAT";
    public static final String PROTOCOL_OPENAI_RESPONSE = "OPENAI_RESPONSE";
    public static final String PROTOCOL_ANTHROPIC_MESSAGES = "ANTHROPIC_MESSAGES";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String vendor;
    private String baseUrl;
    /** OPENAI_CHAT | OPENAI_RESPONSE | ANTHROPIC_MESSAGES */
    private String protocol;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String apiKey;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long updatedBy;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public String protocolOrDefault() {
        return protocol == null || protocol.isBlank() ? PROTOCOL_OPENAI_CHAT : protocol;
    }
}
