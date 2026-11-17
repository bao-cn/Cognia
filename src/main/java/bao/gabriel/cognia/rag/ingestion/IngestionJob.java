package bao.gabriel.cognia.rag.ingestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_ingestion_job")
public class IngestionJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long channelId;
    private String step;
    private String status;
    private Integer attempts;
    private Integer maxAttempts;
    private LocalDateTime availableAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
