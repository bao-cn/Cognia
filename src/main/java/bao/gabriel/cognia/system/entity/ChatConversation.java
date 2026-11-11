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
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String publicId;
    private Long userId;
    private String title;
    private Long modelId;
    private String channelIds;
    private Boolean pinned;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime pinnedTime;
    private Boolean archived;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime archivedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
