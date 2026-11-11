package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_favorite")
public class Favorite {

    public static final String TYPE_CONVERSATION = "CONVERSATION";
    public static final String TYPE_MESSAGE = "MESSAGE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String itemType;
    private Long conversationId;
    private Long messageId;
    private String title;
    private String content;
    private LocalDateTime createTime;
}
