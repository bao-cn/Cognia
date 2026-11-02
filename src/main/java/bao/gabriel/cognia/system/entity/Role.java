package bao.gabriel.cognia.system.entity;

import bao.gabriel.cognia.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * RBAC role, identified by a stable business code such as {@code ADMIN}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer sort;

    /**
     * 1 = enabled, 0 = disabled.
     */
    private Integer status;

    /**
     * Built-in roles (ADMIN/USER) cannot be deleted.
     */
    private Boolean builtin;
}
