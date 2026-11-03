package bao.gabriel.cognia.system.dto.chat;

public record ChatAttachmentResponse(
        String kind,
        String name,
        String mimeType,
        long size,
        String dataUrl,
        String text
) {
}
