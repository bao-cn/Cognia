package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.service.DocumentIngestionService;
import bao.gabriel.cognia.system.service.DocumentStateMachine;
import bao.gabriel.cognia.system.service.DocumentStateMachine.DocumentStatus;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.system.service.RagSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
 * Turns an uploaded document into chunk rows in {@code document_chunk}. The
 * document table only keeps document-level metadata; every chunk stores its
 * own metadata and vector so retrieval can filter by channel permissions and
 * mark expired content without joining a giant JSON blob.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KbDocumentMapper documentMapper;
    private final MinioStorageService storageService;
    private final DocumentStateMachine stateMachine;
    private final NotificationService notificationService;
    private final RagSettingService ragSettingService;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void ingest(Long documentId) {
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || DocumentStatus.DELETED.name().equals(document.getStatus())) {
            return;
        }
        try {
            stateMachine.transition(document, DocumentStatus.PROCESSING, null);
            documentMapper.updateById(document);

            byte[] bytes = storageService.readObject(document.getObjectKey());
            String text = extractText(document, bytes);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("文档中没有可向量化的文本内容");
            }
            RagSetting setting = ragSettingService.getSetting();
            List<String> chunks = split(text, setting.chunkSize(), setting.chunkOverlap());
            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档切片结果为空");
            }
            List<EmbeddingService.EmbeddedText> embeddings = embeddingService.embedAll(chunks);
            jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", document.getId());
            LocalDateTime now = LocalDateTime.now();
            for (int index = 0; index < chunks.size(); index++) {
                String chunk = chunks.get(index);
                EmbeddingService.EmbeddedText embedded = embeddings.get(index);
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
                metadata.put("embedding_model", embedded.model());
                metadata.put("expiration_time", document.getExpirationTime() == null
                        ? null : document.getExpirationTime().toString());
                metadata.put("extra", Map.of("size", document.getSize() == null ? 0 : document.getSize()));
                jdbcTemplate.update("""
                                INSERT INTO document_chunk
                                    (document_id, chunk_id, chunk_index, chunk_text, chunk_text_hash,
                                     embedding_model, embedding, page_number, title, author, document_type,
                                     s3_path, expiration_time, version, metadata, create_time)
                                VALUES (?, ?::uuid, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                                """,
                        document.getId(), UUID.randomUUID(), index, chunk, hash,
                        embedded.model(), toVector(embedded.vector()), 1, title(document),
                        document.getAuthor(), documentType(document), document.getObjectKey(),
                        document.getExpirationTime(), document.getVersion() == null ? 1 : document.getVersion(),
                        JSON.writeValueAsString(metadata), now);
            }
            document.setErrorMessage(null);
            document.setProcessedAt(now);
            stateMachine.transition(document, DocumentStatus.READY, null);
            documentMapper.updateById(document);
            log.info("Document {} ingested with {} chunks (dimension {})", documentId, chunks.size(),
                    embeddings.isEmpty() ? 0 : embeddings.get(0).dimension());
        } catch (Exception ex) {
            log.warn("Document {} ingestion failed", documentId, ex);
            KbDocument latest = documentMapper.selectById(documentId);
            if (latest == null) {
                return;
            }
            try {
                stateMachine.transition(latest, DocumentStatus.FAILED, truncate(ex.getMessage(), 900));
                documentMapper.updateById(latest);
            } catch (Exception ignored) {
                latest.setStatus(DocumentStatus.FAILED.name());
                latest.setErrorMessage(truncate(ex.getMessage(), 900));
                documentMapper.updateById(latest);
            }
            if (latest.getCreatedBy() != null) {
                notificationService.notify(latest.getCreatedBy(), "DOCUMENT_FAILED", "文档处理失败",
                        latest.getName() + " 向量化失败，请重试。",
                        Map.of("documentId", latest.getId(), "channelId",
                                latest.getChannelId() == null ? 0L : latest.getChannelId()));
            }
        }
    }

    private String extractText(KbDocument document, byte[] bytes) {
        String name = document.getName() == null ? "" : document.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                return extractPdf(bytes);
            }
            if (name.endsWith(".docx")) {
                return extractDocx(bytes);
            }
            if (name.endsWith(".xlsx")) {
                return extractXlsx(bytes);
            }
            if (name.endsWith(".pptx")) {
                return extractPptx(bytes);
            }
            if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".markdown")
                    || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".csv")
                    || name.endsWith(".json")) {
                String text = new String(bytes, StandardCharsets.UTF_8);
                return name.endsWith(".html") || name.endsWith(".htm") ? text.replaceAll("<[^>]+>", " ") : text;
            }
            throw new IllegalStateException("暂不支持该文件类型的自动解析");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("文档解析失败：" + ex.getMessage(), ex);
        }
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        text.append(cell.getText()).append('\t');
                    }
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }

    private String extractXlsx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            for (Sheet sheet : workbook) {
                text.append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        text.append(cell.toString()).append('\t');
                    }
                    text.append('\n');
                }
            }
        }
        return text.toString();
    }

    private String extractPptx(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            for (XSLFSlide slide : slideShow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        text.append(textShape.getText()).append('\n');
                    }
                }
            }
        }
        return text.toString();
    }

    private List<String> split(String text, int chunkSize, int chunkOverlap) {
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSize);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - chunkOverlap, start + 1);
        }
        return chunks;
    }

    private String title(KbDocument document) {
        if (document.getTitle() != null && !document.getTitle().isBlank()) {
            return document.getTitle();
        }
        String name = document.getName() == null ? "" : document.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String documentType(KbDocument document) {
        if (document.getDocumentType() != null && !document.getDocumentType().isBlank()) {
            return document.getDocumentType();
        }
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

    private String toVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
