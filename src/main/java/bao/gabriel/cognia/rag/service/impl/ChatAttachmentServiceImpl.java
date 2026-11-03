package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.document.DocumentTextExtractor;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.service.ChatAttachmentService;
import bao.gabriel.cognia.system.dto.chat.ChatAttachmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_DOCUMENT_BYTES = 25L * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 30_000;
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/gif", "image/png", "image/webp");

    private final DocumentTextExtractor textExtractor;

    @Override
    public ChatAttachmentResponse prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件为空");
        }
        String name = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim() : "attachment";
        String mime = StringUtils.hasText(file.getContentType())
                ? file.getContentType().toLowerCase(Locale.ROOT) : guessMime(name);
        if ("image/jpg".equals(mime)) {
            mime = "image/jpeg";
        }
        if ("application/octet-stream".equals(mime)) {
            String guessed = guessMime(name);
            if (!"application/octet-stream".equals(guessed)) {
                mime = guessed;
            }
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw BusinessException.badRequest("读取文件失败：" + ex.getMessage());
        }
        if (IMAGE_TYPES.contains(mime)) {
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw BusinessException.badRequest("图片不能超过 10 MB");
            }
            String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return new ChatAttachmentResponse("IMAGE", name, mime, bytes.length, dataUrl, null);
        }
        if (bytes.length > MAX_DOCUMENT_BYTES) {
            throw BusinessException.badRequest("文档不能超过 25 MB");
        }
        String text;
        try {
            text = textExtractor.extract(name, bytes);
        } catch (Exception ex) {
            throw BusinessException.badRequest("文档解析失败：" + ex.getMessage());
        }
        if (!StringUtils.hasText(text)) {
            throw BusinessException.badRequest("文档中没有可提取的文本；图片型 PDF 需要配置 OCR 能力");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_CHARS) {
            trimmed = trimmed.substring(0, MAX_TEXT_CHARS);
        }
        return new ChatAttachmentResponse("TEXT", name, mime, bytes.length, null, trimmed);
    }

    private String guessMime(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }
}
