package bao.gabriel.cognia.system.dto.channel;

public record ChannelSummaryResponse(
        String id,
        String ownerType,
        String ownerId,
        String ownerName,
        String name,
        String slug,
        String description,
        boolean isPublic,
        boolean pinned,
        int pinnedOrder,
        long documentCount,
        String readmeKey,
        String agentKey,
        String agentName,
        String agentDescription,
        String agentTags,
        String updateTime
) {
}
