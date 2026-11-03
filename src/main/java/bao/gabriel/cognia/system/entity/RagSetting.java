package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_rag_setting")
public class RagSetting {

    public static final long SINGLETON_ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long chatModelId;
    private Long embeddingModelId;
    private Boolean rerankEnabled;
    private Long rerankModelId;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer retrievalTopK;
    private Integer retrievalTopN;
    private String webSearchEndpoint;
    private String webSearchApiKey;
    private Boolean webSearchEnabled;
    private Boolean allowUserWebSearch;
    private Integer contextCompressionThreshold;
    private String compressionPrompt;
    private Boolean semanticCacheEnabled;
    private Boolean retrievalCacheEnabled;
    private Double semanticCacheThreshold;
    private Integer semanticCacheTtlMinutes;
    private Integer semanticCacheMaxEntries;
    private Integer retrievalCacheTtlMinutes;
    private Integer retrievalCacheMaxEntries;
    private Boolean titleGenerationEnabled;
    private Long updatedBy;
    private LocalDateTime updateTime;

    public int chunkSize() {
        return chunkSize == null ? 1200 : chunkSize;
    }

    public int chunkOverlap() {
        return chunkOverlap == null ? 120 : chunkOverlap;
    }

    public int retrievalTopK() {
        return retrievalTopK == null ? 20 : retrievalTopK;
    }

    public int retrievalTopN() {
        return retrievalTopN == null ? 5 : retrievalTopN;
    }

    public boolean rerankEnabled() {
        return Boolean.TRUE.equals(rerankEnabled);
    }

    public boolean webSearchEnabled() {
        return !Boolean.FALSE.equals(webSearchEnabled);
    }

    public boolean allowUserWebSearch() {
        return !Boolean.FALSE.equals(allowUserWebSearch);
    }

    public int contextCompressionThreshold() {
        return contextCompressionThreshold == null ? 80 : contextCompressionThreshold;
    }

    public String compressionPrompt() {
        return StringUtils.hasText(compressionPrompt) ? compressionPrompt : DEFAULT_COMPRESSION_PROMPT;
    }

    public boolean semanticCacheEnabled() {
        return !Boolean.FALSE.equals(semanticCacheEnabled);
    }

    public boolean retrievalCacheEnabled() {
        return !Boolean.FALSE.equals(retrievalCacheEnabled);
    }

    public double semanticCacheThreshold() {
        return semanticCacheThreshold == null ? 0.92 : semanticCacheThreshold;
    }

    public int semanticCacheTtlMinutes() {
        return semanticCacheTtlMinutes == null ? 60 : semanticCacheTtlMinutes;
    }

    public int semanticCacheMaxEntries() {
        return semanticCacheMaxEntries == null ? 500 : semanticCacheMaxEntries;
    }

    public int retrievalCacheTtlMinutes() {
        return retrievalCacheTtlMinutes == null ? 1440 : retrievalCacheTtlMinutes;
    }

    public int retrievalCacheMaxEntries() {
        return retrievalCacheMaxEntries == null ? 1000 : retrievalCacheMaxEntries;
    }

    public boolean titleGenerationEnabled() {
        return !Boolean.FALSE.equals(titleGenerationEnabled);
    }

    /**
     * Default Chinese prompt used when the administrator has not customised the
     * conversation-compression prompt yet.
     */
    public static final String DEFAULT_COMPRESSION_PROMPT = """
            请把下面的多轮对话历史压缩成一段简洁的中文摘要，要求：
            1. 保留用户的核心目标、关键事实、数字、约束条件与偏好；
            2. 保留已经确认的结论、做出的决定以及尚未完成的事项；
            3. 删除寒暄、重复表达和无关内容，不要编造历史中不存在的信息；
            4. 只输出摘要正文，不要添加标题或解释。""";
}
