package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_team_member")
public class TeamMember {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_EDITOR = "EDITOR";
    public static final String ROLE_MEMBER = "MEMBER";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long teamId;

    private Long userId;

    private String role;

    private LocalDateTime joinedAt;
}
