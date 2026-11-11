package bao.gabriel.cognia.system.dto.chat;

/**
 * An attachment prepared by the frontend. Images carry a data URL; documents
 * carry already-extracted plain text.
 */
public record ChatAttachmentRequest(
        String type,
        String name,
        String mimeType,
        String dataUrl,
        String text,
        String url,
        String key
) {
}
