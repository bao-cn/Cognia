package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_team_invitation")
public class TeamInvitation {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_DECLINED = "DECLINED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long teamId;

    private Long inviterId;

    private Long inviteeId;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
