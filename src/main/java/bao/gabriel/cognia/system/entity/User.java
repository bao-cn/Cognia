package bao.gabriel.cognia.system.entity;

import bao.gabriel.cognia.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Application user. Credentials are stored as BCrypt hashes.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /**
     * BCrypt hash. Never expose this field through an API response.
     */
    private String password;

    private String nickname;

    private String email;

    private String phone;

    private String avatar;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String gender;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bio;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String company;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String hometown;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String timezone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String socialLinks;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String readme;

    /**
     * 1 = enabled, 0 = disabled.
     */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    /**
     * Set by the deleted-owner cleanup task once the user's stored files have
     * been removed. Null means cleanup is still pending.
     */
    private LocalDateTime filesCleanedAt;

    private Boolean autoWebSearch;

    private String twoFactorSecret;

    private Boolean twoFactorEnabled;

    public User(String username, String email, String phone) {
        this.username = username;
        this.email = email;
        this.phone = phone;
    }

    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
