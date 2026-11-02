package bao.gabriel.cognia.system.dto.profile;

public record ChannelCard(
        String id,
        String name,
        String slug,
        String description,
        boolean isPublic,
        boolean pinned,
        int pinnedOrder,
        long documentCount,
        String updateTime
) {
}
