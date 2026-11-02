package bao.gabriel.cognia.system.dto.channel;

public record FolderResponse(
        String id,
        String parentId,
        String name,
        int depth
) {
}
