package bao.gabriel.cognia.common.ocr;

public interface OcrService {

    /**
     * Extract text from a scanned/image-only PDF. Returns an empty string when
     * no OCR backend is available.
     */
    String ocrPdf(byte[] pdfBytes);

    String ocrImage(byte[] imageBytes, String mimeType);
}
