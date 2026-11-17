package bao.gabriel.cognia.rag.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_embedding_health")
public class EmbeddingHealth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelLabel;
    private Boolean success;
    private Long latencyMs;
    private String errorMessage;
    private LocalDateTime createTime;
}
