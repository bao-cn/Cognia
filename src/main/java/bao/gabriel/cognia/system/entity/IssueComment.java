package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("kb_issue_comment")
public class IssueComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long issueId;
    private Long authorId;
    private String body;
    private Long quoteCommentId;
    private LocalDateTime createTime;
}
