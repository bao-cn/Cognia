package bao.gabriel.cognia.system.dto.channel;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** folderId = null moves documents to the channel root. */
public record MoveDocumentsRequest(@NotEmpty(message = "请选择要移动的文件") List<Long> documentIds,
                                   Long folderId) {
}
