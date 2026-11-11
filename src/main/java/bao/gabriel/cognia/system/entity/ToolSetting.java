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
 * Built-in tool configuration. Credentials for the web search provider live
 * here instead of the retrieval settings: they configure a tool, not a
 * retrieval parameter.
 */
@Data
@NoArgsConstructor
@TableName("sys_tool_setting")
public class ToolSetting {

    public static final long SINGLETON_ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private Boolean webSearchEnabled;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String webSearchEndpoint;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String webSearchApiKey;
    private Boolean webFetchEnabled;
    private Integer maxResults;
    private Long updatedBy;
    private LocalDateTime updateTime;

    public boolean webSearchEnabled() {
        return Boolean.TRUE.equals(webSearchEnabled);
    }

    public boolean webFetchEnabled() {
        return !Boolean.FALSE.equals(webFetchEnabled);
    }

    /** Search only works with a configured endpoint; no implicit fallback. */
    public boolean webSearchUsable() {
        return webSearchEnabled() && webSearchEndpoint != null && !webSearchEndpoint.isBlank();
    }

    public int maxResultsOr(int fallback) {
        return maxResults == null || maxResults < 1 ? fallback : Math.min(10, maxResults);
    }
}
