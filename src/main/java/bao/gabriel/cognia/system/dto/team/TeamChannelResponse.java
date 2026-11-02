package bao.gabriel.cognia.system.dto.team;

public record TeamChannelResponse(
        String id,
        String name,
        String description,
        boolean isPublic,
        boolean pinned,
        int pinnedOrder,
        String createdBy,
        String creatorName,
        String createTime
) {
}
