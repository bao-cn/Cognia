package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@TableName("document_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private UUID chunkId;
    private Integer chunkIndex;
    private String chunkText;
    private String chunkTextHash;
    private String embeddingModel;
    private String embedding;
    private Integer pageNumber;
    private String title;
    private String author;
    private String documentType;
    private String s3Path;
    private LocalDateTime expirationTime;
    private Integer version;
    private String metadata;
    private LocalDateTime createTime;
}
