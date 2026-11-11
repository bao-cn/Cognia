package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_oauth_provider")
public class OAuthProvider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String providerType;
    private Boolean enabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String clientId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String clientSecret;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String issuer;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String authorizationUri;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tokenUri;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String userinfoUri;
    private String scopes;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String redirectUri;
    private String usernameClaim;
    private String emailClaim;
    private String nameClaim;
    private Boolean autoCreate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean enabled() { return Boolean.TRUE.equals(enabled); }
    public boolean autoCreate() { return Boolean.TRUE.equals(autoCreate); }
}
