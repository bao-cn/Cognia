package bao.gabriel.cognia.system.entity;

import bao.gabriel.cognia.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Fine grained permission such as {@code user:read}. Permissions are attached
 * to roles and checked with Spring Security method annotations.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class Permission extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    /**
     * API / MENU / BUTTON.
     */
    private String type;

    /**
     * Logical resource, for example {@code /api/v1/users}.
     */
    private String resource;

    /**
     * HTTP method for API permissions (GET, POST, ...).
     */
    private String method;

    private Long parentId;

    private Integer sort;
}
