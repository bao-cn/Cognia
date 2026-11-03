package bao.gabriel.cognia.common.document;

import bao.gabriel.cognia.common.ocr.OcrService;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Shared text extraction used by chat attachments and knowledge-base
 * ingestion. Image-only PDFs fall back to {@link OcrService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTextExtractor {

    private final OcrService ocrService;

    public String extract(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
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
                return name.endsWith(".html") || name.endsWith(".htm")
                        ? text.replaceAll("<[^>]+>", " ") : text;
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
            String text = stripper.getText(document);
            if (StringUtils.hasText(text) && text.trim().length() >= 20) {
                return text;
            }
            String ocr = ocrService.ocrPdf(bytes);
            if (StringUtils.hasText(ocr)) {
                return ocr;
            }
            return text;
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
}
