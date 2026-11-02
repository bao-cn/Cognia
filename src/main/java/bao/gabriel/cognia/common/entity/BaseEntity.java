package bao.gabriel.cognia.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Common audit columns shared by persistent entities.
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * PostgreSQL boolean based soft delete. MyBatis-Plus appends
     * {@code is_deleted = false} to every generated query.
     */
    @TableLogic(value = "false", delval = "true")
    @TableField(select = false)
    private Boolean isDeleted;

    private String remark;
}
