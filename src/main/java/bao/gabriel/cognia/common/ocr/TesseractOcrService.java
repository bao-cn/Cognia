package bao.gabriel.cognia.common.ocr;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.entity.ModelConfig;
import bao.gabriel.cognia.system.entity.ModelProvider;
import bao.gabriel.cognia.system.mapper.ModelConfigMapper;
import bao.gabriel.cognia.system.mapper.ModelProviderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * OCR implementation with two backends:
 * <ol>
 *   <li>the local {@code tesseract} CLI when it is installed;</li>
 *   <li>a vision-capable chat model configured by an administrator.</li>
 * </ol>
 * This keeps image-PDF ingestion working on servers without a local OCR
 * binary, as long as a vision model is available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesseractOcrService implements OcrService {

    private static final int PDF_DPI = 180;
    private static final int MAX_OCR_CHARS = 40_000;

    private final OpenAiCompatibleClient client;
    private final ModelConfigMapper modelConfigMapper;
    private final ModelProviderMapper providerMapper;
    private final SecurityCryptoService cryptoService;

    private volatile Boolean tesseractAvailable;

    @Override
    public String ocrPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, PDF_DPI, ImageType.RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                String text = ocrImage(png.toByteArray(), "image/png");
                if (StringUtils.hasText(text)) {
                    result.append(text).append('\n');
                }
                if (result.length() >= MAX_OCR_CHARS) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("PDF OCR failed: {}", ex.getMessage());
        }
        return truncate(result.toString());
    }

    @Override
    public String ocrImage(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return "";
        }
        String local = tesseract(imageBytes);
        if (StringUtils.hasText(local)) {
            return truncate(local);
        }
        return truncate(visionOcr(imageBytes, mimeType));
    }

    private String tesseract(byte[] imageBytes) {
        if (!tesseractAvailable()) {
            return "";
        }
        String text = runTesseract(imageBytes, "chi_sim+eng");
        if (!StringUtils.hasText(text)) {
            text = runTesseract(imageBytes, "eng");
        }
        return text;
    }

    private boolean tesseractAvailable() {
        Boolean cached = tesseractAvailable;
        if (cached != null) {
            return cached;
        }
        boolean available;
        try {
            Process process = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true).start();
            available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ex) {
            available = false;
        }
        if (!available) {
            log.info("tesseract CLI is not available; falling back to vision-model OCR");
        }
        tesseractAvailable = available;
        return available;
    }

    private String runTesseract(byte[] imageBytes, String language) {
        Process process = null;
        try {
            process = new ProcessBuilder("tesseract", "stdin", "stdout", "-l", language)
                    .redirectErrorStream(true)
                    .start();
            try (InputStream stdin = process.getInputStream()) {
                process.getOutputStream().write(imageBytes);
                process.getOutputStream().close();
                if (!process.waitFor(90, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return "";
                }
                String output = new String(stdin.readAllBytes(), StandardCharsets.UTF_8);
                return process.exitValue() == 0 ? output : "";
            }
        } catch (Exception ex) {
            log.debug("tesseract OCR failed: {}", ex.getMessage());
            return "";
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String visionOcr(byte[] imageBytes, String mimeType) {
        try {
            ModelConfig model = modelConfigMapper.selectOne(new LambdaQueryWrapper<ModelConfig>()
                    .eq(ModelConfig::getModelType, ModelConfig.TYPE_CHAT)
                    .eq(ModelConfig::getEnabled, true)
                    .eq(ModelConfig::getSupportsVision, true)
                    .orderByAsc(ModelConfig::getId)
                    .last("LIMIT 1"));
            if (model == null) {
                log.debug("No vision-capable chat model configured for OCR");
                return "";
            }
            ModelProvider provider = providerMapper.selectById(model.getProviderId());
            if (provider == null || !provider.enabled()) {
                return "";
            }
            String mime = StringUtils.hasText(mimeType) ? mimeType : "image/png";
            String dataUrl = "data:" + mime + ";base64,"
                    + Base64.getEncoder().encodeToString(imageBytes);
            ObjectNode body = client.mapper().createObjectNode();
            body.put("model", model.getModelId());
            body.put("stream", false);
            body.put("temperature", 0);
            body.put("max_tokens", 4096);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system")
                    .put("content", "You are an OCR engine. Return only the text visible in the image, preserving line breaks. Do not explain.");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");
            content.addObject().put("type", "text").put("text", "请提取图片中的全部文字。");
            ObjectNode image = content.addObject();
            image.put("type", "image_url");
            image.putObject("image_url").put("url", dataUrl);
            JsonNode response = client.postJson(provider.getBaseUrl(), "/chat/completions",
                    cryptoService.decrypt(provider.getApiKey()), body, Duration.ofSeconds(180));
            return response.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception ex) {
            log.warn("Vision OCR failed: {}", ex.getMessage());
            return "";
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() > MAX_OCR_CHARS ? trimmed.substring(0, MAX_OCR_CHARS) : trimmed;
    }
}
