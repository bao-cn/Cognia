package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@TableName("kb_issue_label_link")
public class IssueLabelLink {
    private Long issueId;
    private Long labelId;
}
