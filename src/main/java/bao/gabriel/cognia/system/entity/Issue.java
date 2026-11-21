package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("kb_issue")
public class Issue {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long channelId;
    private Long number;
    private String title;
    private String body;
    private String status;
    private Long authorId;
    private Long assigneeId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime closedAt;

    public boolean open() { return OPEN.equals(status); }
}
