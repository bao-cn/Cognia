package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_token_usage")
public class TokenUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private Long modelConfigId;
    private Long providerId;
    private String modelNameSnapshot;
    private Long inputTokens;
    private Long outputTokens;
    private Long chargedTokens;
    private Long durationMs;
    private String status;
    private Long conversationId;
    private LocalDateTime createdAt;
}
