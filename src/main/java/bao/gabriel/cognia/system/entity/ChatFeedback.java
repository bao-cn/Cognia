package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("chat_feedback")
public class ChatFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long conversationId;
    private Long userId;
    private String feedbackType;
    private String reason;
    private String problemType;
    private String status;
    private String note;
    private String traceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
