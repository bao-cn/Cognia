package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One end-to-end chat trace for the admin-only dev console. */
@Data
@NoArgsConstructor
@TableName("chat_trace")
public class ChatTrace {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String conversationId;
    private Long userId;
    private Long modelId;
    private String modelName;
    private String protocol;
    private String question;
    private String answer;
    private String status;
    private Long durationMs;
    private String traceJson;
    private LocalDateTime createTime;
}
