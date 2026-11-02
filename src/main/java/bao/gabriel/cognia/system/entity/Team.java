package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A Cognia team. Every knowledge space belongs to a team; there are no
 * personal channels, so even a single-user workspace is a team with one member.
 */
@Data
@NoArgsConstructor
@TableName("sys_team")
public class Team {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableLogic
    private Boolean isDeleted;

    private LocalDateTime deletedAt;

    /**
     * Set by the deleted-owner cleanup task after kb/avatar objects are gone.
     */
    private LocalDateTime filesCleanedAt;

    private String name;

    private String slug;

    private String description;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String logoUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String readme;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customLinks;

    private Boolean joinRequestsEnabled;

    private String defaultMemberRole;

    private Long ownerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
