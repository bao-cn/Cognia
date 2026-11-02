package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single-row site configuration (id = 1). SMTP credentials are maintained in
 * the database; the password column stores an AES-GCM ciphertext.
 */
@Data
@NoArgsConstructor
@TableName("sys_site_setting")
public class SiteSetting {

    public static final long SINGLETON_ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String logoUrl;
    private String siteName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String siteDescription;
    private String siteKeywords;
    private String routeSuffix;
    private String defaultLanguage;
    private String timezone;
    private String allowedUploadTypes;

    private Boolean registrationEnabled;
    private Boolean registrationEmailVerification;

    /**
     * Legacy column name {@code verification_code_enabled}; controls whether
     * sending a registration email code requires Cloudflare Turnstile.
     */
    @TableField("verification_code_enabled")
    private Boolean registerEmailCaptchaEnabled;

    private Boolean loginCaptchaEnabled;
    private Boolean registrationEmailWhitelistEnabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String registrationEmailWhitelist;

    private Boolean appleLoginEnabled;
    private Boolean googleLoginEnabled;

    /** Cloudflare Turnstile public site key; the secret is stored encrypted. */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String turnstileSiteKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String turnstileSecretKey;

    private Boolean smtpEnabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String smtpHost;
    private Integer smtpPort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String smtpUsername;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String smtpPassword;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String smtpFrom;
    private Boolean smtpSsl;
    private Boolean smtpStarttls;
    private Boolean smtpAuth;

    private Integer maxFolderDepth;
    private Integer recycleRetentionDays;

    private Integer teamCreationLimit;
    private Integer teamJoinLimit;

    private Long updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;

    public boolean registrationEnabled() {
        return Boolean.TRUE.equals(registrationEnabled);
    }

    public boolean emailVerificationRequired() {
        return Boolean.TRUE.equals(registrationEmailVerification);
    }

    public boolean registerEmailCaptchaEnabled() {
        return Boolean.TRUE.equals(registerEmailCaptchaEnabled);
    }

    public boolean loginCaptchaEnabled() {
        return Boolean.TRUE.equals(loginCaptchaEnabled);
    }

    public boolean emailWhitelistEnabled() {
        return Boolean.TRUE.equals(registrationEmailWhitelistEnabled);
    }

    public boolean smtpEnabled() {
        return Boolean.TRUE.equals(smtpEnabled);
    }

    public int maxFolderDepth() {
        return maxFolderDepth == null || maxFolderDepth < 1 ? 5 : Math.min(maxFolderDepth, 10);
    }

    public int recycleRetentionDays() {
        return recycleRetentionDays == null || recycleRetentionDays < 1 ? 30 : recycleRetentionDays;
    }

    public int teamCreationLimit() {
        return teamCreationLimit == null || teamCreationLimit < 1 ? 5 : teamCreationLimit;
    }

    public int teamJoinLimit() {
        return teamJoinLimit == null || teamJoinLimit < 1 ? 10 : teamJoinLimit;
    }
}
