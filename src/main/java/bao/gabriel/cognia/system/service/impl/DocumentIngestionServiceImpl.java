package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.document.DocumentTextExtractor;
import bao.gabriel.cognia.common.document.StructuralChunker;
import bao.gabriel.cognia.rag.service.ImageCaptionService;
import bao.gabriel.cognia.rag.ingestion.IngestionQueueService;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.RagCacheService;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.service.DocumentIngestionService;
import bao.gabriel.cognia.system.service.DocumentStateMachine;
import bao.gabriel.cognia.system.service.DocumentProcessingNotifier;
import bao.gabriel.cognia.system.service.DocumentStateMachine.DocumentStatus;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.RagSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Parsing and embedding are separate queue steps. A failed step only retries
 * that step; chunks remain PENDING and are never treated as valid vectors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KbDocumentMapper documentMapper;
    private final MinioStorageService storageService;
    private final DocumentStateMachine stateMachine;
    private final RagSettingService ragSettingService;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final RagCacheService ragCacheService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ImageCaptionService imageCaptionService;
    private final IngestionQueueService queueService;
    private final DocumentProcessingNotifier processingNotifier;

    @Override
    public void ingest(Long documentId) {
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED.name().equals(document.getStatus())) {
            return;
        }
        if (!queueService.enqueue(documentId, document.getChannelId(), "PARSE")) {
            throw new IllegalStateException("队列已满，请稍后重试");
        }
        log.info("Document {} queued for parsing", documentId);
    }

    /** Queue worker step 1: extract text and create PENDING chunk rows. */
    public void parse(Long documentId) {
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED.name().equals(document.getStatus())) {
            return;
        }
        stateMachine.transition(document, DocumentStatus.PROCESSING, null);
        documentMapper.updateById(document);

        byte[] bytes = storageService.readObject(document.getObjectKey());
        boolean image = isImage(document);
        String text;
        if (image) {
            text = imageCaptionService.captionIfConfigured(document, bytes);
            if (text == null) {
                log.info("Image embedding disabled; document {} kept as upload-only", documentId);
                markReady(document);
                return;
            }
        } else {
            text = documentTextExtractor.extract(document.getName(), bytes);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(image
                    ? "图生文模型未返回内容，请检查图片理解模型配置后重试"
                    : "文档中没有可向量化的文本内容");
        }
        RagSetting setting = ragSettingService.getSetting();
        List<String> chunks = StructuralChunker.chunk(text, document.getName(), setting.chunkSize(), setting.chunkOverlap());
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文档切片结果为空");
        }
        String modelLabel = embeddingService.activeModelLabel();
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", document.getId());
        invalidateRetrievalCache(document.getId());
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            String hash = sha256(chunk);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("file_name", document.getName());
            metadata.put("page_number", 1);
            metadata.put("chunk_id", UUID.randomUUID().toString());
            metadata.put("chunk_index", index);
            metadata.put("chunk_text_hash", hash);
            metadata.put("version", document.getVersion() == null ? 1 : document.getVersion());
            metadata.put("document_id", document.getId());
            metadata.put("channel_id", document.getChannelId());
            metadata.put("owner_id", document.getOwnerId());
            metadata.put("s3_path", document.getObjectKey());
            metadata.put("document_type", documentType(document));
            metadata.put("title", title(document));
            metadata.put("author", document.getAuthor());
            metadata.put("embedding_model", modelLabel);
            metadata.put("expiration_time", document.getExpirationTime() == null
                    ? null : document.getExpirationTime().toString());
            metadata.put("extra", Map.of("size", document.getSize() == null ? 0 : document.getSize()));
            jdbcTemplate.update("""
                            INSERT INTO document_chunk
                                (document_id, chunk_id, chunk_index, chunk_text, chunk_text_hash,
                                 embedding_model, embedding, embedding_status, page_number, title, author,
                                 document_type, s3_path, expiration_time, version, metadata, create_time)
                            VALUES (?, ?::uuid, ?, ?, ?, ?, NULL, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                            """,
                    document.getId(), UUID.randomUUID(), index, chunk, hash,
                    modelLabel, 1, title(document), document.getAuthor(), documentType(document),
                    document.getObjectKey(), document.getExpirationTime(),
                    document.getVersion() == null ? 1 : document.getVersion(),
                    json(metadata), now);
        }
        log.info("Document {} parsed into {} pending chunks", documentId, chunks.size());
        queueService.enqueue(documentId, document.getChannelId(), "EMBED");
    }

    /** Queue worker step 2: embed PENDING chunks and mark the document READY. */
    public void embed(Long documentId) {
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED.name().equals(document.getStatus())) {
            return;
        }
        List<ChunkRow> chunks = jdbcTemplate.query("""
                        SELECT id, chunk_text FROM document_chunk
                        WHERE document_id = ? AND (embedding IS NULL OR embedding_status <> 'READY')
                        ORDER BY chunk_index
                        """, (rs, rowNum) -> new ChunkRow(rs.getLong("id"), rs.getString("chunk_text")), documentId);
        if (chunks.isEmpty()) {
            markReady(document);
            return;
        }
        List<String> texts = chunks.stream().map(ChunkRow::text).toList();
        List<EmbeddingService.EmbeddedText> embeddings = embeddingService.embedAll(texts);
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding 返回数量与切片数量不一致");
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < chunks.size(); i++) {
            EmbeddingService.EmbeddedText item = embeddings.get(i);
            jdbcTemplate.update("""
                            UPDATE document_chunk SET embedding = ?::vector, embedding_model = ?,
                                embedding_status = 'READY' WHERE id = ?
                            """, toVector(item.vector()), item.model(), chunks.get(i).id());
        }
        markReady(document);
        log.info("Document {} embedded with {} chunks", documentId, chunks.size());
    }

    private void markReady(KbDocument document) {
        document.setErrorMessage(null);
        document.setProcessedAt(LocalDateTime.now());
        if (DocumentStatus.READY.name().equals(document.getStatus())) {
            documentMapper.updateById(document);
            processingNotifier.finished(document, true);
            return;
        }
        stateMachine.transition(document, DocumentStatus.READY, null);
        documentMapper.updateById(document);
        processingNotifier.finished(document, true);
    }

    private record ChunkRow(long id, String text) {}


    private boolean isImage(KbDocument document) {
        String type = document.getContentType();
        if (type != null && type.toLowerCase().startsWith("image/")) return true;
        String name = document.getName() == null ? "" : document.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp");
    }

    private String title(KbDocument document) {
        if (document.getTitle() != null && !document.getTitle().isBlank()) return document.getTitle();
        String name = document.getName() == null ? "" : document.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String documentType(KbDocument document) {
        if (document.getDocumentType() != null && !document.getDocumentType().isBlank()) return document.getDocumentType();
        String name = document.getName() == null ? "" : document.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "unknown";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("文档元数据序列化失败", ex); }
    }

    private String toVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    private void invalidateRetrievalCache(Long documentId) {
        try { ragCacheService.invalidateDocument(documentId); }
        catch (Exception ex) { log.warn("Unable to invalidate retrieval cache for document {}: {}", documentId, ex.getMessage()); }
    }
}
