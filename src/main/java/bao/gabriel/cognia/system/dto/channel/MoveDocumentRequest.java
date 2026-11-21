package bao.gabriel.cognia.system.dto.channel;

/** folderId = null moves the document to the channel root. */
public record MoveDocumentRequest(Long folderId) {
}
