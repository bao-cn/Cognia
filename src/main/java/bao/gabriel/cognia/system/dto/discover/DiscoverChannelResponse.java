package bao.gabriel.cognia.system.dto.discover;

/**
 * Card payload for a channel shown in the discover plaza.
 */
public record DiscoverChannelResponse(
        String id,
        String ownerType,
        String ownerId,
        String ownerName,
        String ownerSlug,
        String name,
        String slug,
        String description,
        boolean isPublic,
        boolean featured,
        boolean webSearchEnabled,
        long subscriberCount,
        boolean subscribed,
        boolean notifySubscribed,
        long documentCount,
        String updateTime
) {
}
