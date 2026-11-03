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
 * Unified knowledge-base channel. A channel belongs to a personal user or a
 * team; its public URL is /{ownerName}/{slug}.
 */
@Data
@NoArgsConstructor
@TableName("kb_channel")
public class Channel {

    public static final String OWNER_USER = "USER";
    public static final String OWNER_TEAM = "TEAM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ownerType;
    private Long ownerId;
    private String name;
    private String slug;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private Boolean isPublic;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String readmeKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String agentKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String agentName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String agentDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String agentTags;
    private Boolean pinned;
    private Integer pinnedOrder;
    private Boolean featured;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime featuredTime;
    private Boolean webSearchEnabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isPublic() {
        return Boolean.TRUE.equals(isPublic);
    }

    public boolean featured() {
        return Boolean.TRUE.equals(featured);
    }

    public boolean webSearchEnabled() {
        return !Boolean.FALSE.equals(webSearchEnabled);
    }
}
