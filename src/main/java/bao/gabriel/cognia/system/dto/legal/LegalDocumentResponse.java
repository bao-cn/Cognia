package bao.gabriel.cognia.system.dto.legal;

public record LegalDocumentResponse(
        String id,
        String type,
        String title,
        String content,
        int version,
        String publishedAt,
        String updatedAt
) {
}
