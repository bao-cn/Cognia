package bao.gabriel.cognia.system.dto.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Full settings update from the console. Boolean fields are wrappers so that
 * an omitted value means "false" instead of a JSON deserialization failure.
 */
public record UpdateSettingsRequest(
        @Size(max = 512, message = "Logo 地址过长")
        String logoUrl,

        @NotBlank(message = "站点名称不能为空")
        @Size(max = 64, message = "站点名称长度不能超过 64 个字符")
        String siteName,

        @Size(max = 512, message = "站点描述长度不能超过 512 个字符")
        String siteDescription,

        @Size(max = 20, message = "关键词最多 20 个")
        List<@Size(max = 32, message = "单个关键词长度不能超过 32 个字符") String> keywords,

        @Size(max = 128, message = "路由后缀长度不能超过 128 个字符")
        String routeSuffix,

        @NotBlank(message = "默认语言不能为空")
        @Pattern(regexp = "^(zhCn|enUs)$", message = "默认语言只能为 zhCn 或 enUs")
        String defaultLanguage,

        @NotBlank(message = "时区不能为空")
        @Size(max = 64, message = "时区长度不能超过 64 个字符")
        String timezone,

        @Size(max = 30, message = "上传类型最多 30 个")
        List<@Size(max = 16, message = "单个上传类型长度不能超过 16 个字符") String> allowedUploadTypes,

        Boolean registrationEnabled,
        Boolean registrationEmailVerification,
        Boolean registerEmailCaptchaEnabled,
        Boolean loginCaptchaEnabled,
        Boolean registrationEmailWhitelistEnabled,

        @Size(max = 50, message = "邮箱白名单最多 50 项")
        List<@Size(max = 128, message = "单个白名单条目长度不能超过 128 个字符") String> registrationEmailWhitelist,

        Boolean appleLoginEnabled,
        Boolean googleLoginEnabled,

        @Size(max = 255, message = "Turnstile 站点密钥长度不能超过 255 个字符")
        String turnstileSiteKey,

        @Size(max = 512, message = "Turnstile 密钥长度不能超过 512 个字符")
        String turnstileSecretKey,

        Boolean clearTurnstileSecretKey,

        Boolean smtpEnabled,

        @Size(max = 255, message = "SMTP 主机长度不能超过 255 个字符")
        String smtpHost,

        @Min(value = 1, message = "SMTP 端口最小为 1")
        @Max(value = 65535, message = "SMTP 端口最大为 65535")
        Integer smtpPort,

        @Size(max = 255, message = "SMTP 用户名长度不能超过 255 个字符")
        String smtpUsername,

        @Size(max = 512, message = "SMTP 密码长度不能超过 512 个字符")
        String smtpPassword,

        @Email(message = "发件人邮箱格式不正确")
        @Size(max = 255, message = "发件人邮箱长度不能超过 255 个字符")
        String smtpFrom,

        Boolean smtpSsl,
        Boolean smtpStarttls,
        Boolean smtpAuth,
        Boolean clearSmtpPassword,

        @Min(value = 1, message = "创建团队上限最小为 1")
        @Max(value = 100, message = "创建团队上限最大为 100")
        Integer teamCreationLimit,

        @Min(value = 1, message = "加入团队上限最小为 1")
        @Max(value = 100, message = "加入团队上限最大为 100")
        Integer teamJoinLimit,

        @Min(value = 1, message = "文件夹最大层级最小为 1")
        @Max(value = 10, message = "文件夹最大层级最大为 10")
        Integer maxFolderDepth,

        @Min(value = 1, message = "回收站留存天数最小为 1")
        @Max(value = 3650, message = "回收站留存天数最大为 3650")
        Integer recycleRetentionDays
) {
}
