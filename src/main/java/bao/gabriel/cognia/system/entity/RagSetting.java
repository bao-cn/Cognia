package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
