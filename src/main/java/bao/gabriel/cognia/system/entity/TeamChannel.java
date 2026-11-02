package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_team_channel")
public class TeamChannel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long teamId;

    private String name;

    private String description;

    private Boolean isPublic;

    private Boolean pinned;

    private Integer pinnedOrder;

    private Long createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
