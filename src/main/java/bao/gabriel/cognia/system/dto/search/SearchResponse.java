package bao.gabriel.cognia.system.dto.search;

import java.util.List;

/**
 * Aggregated result for the global command palette.
 */
public record SearchResponse(
        List<ChannelHit> channels,
        List<UserHit> users,
        List<ConversationHit> conversations
) {

    public static SearchResponse empty() {
        return new SearchResponse(List.of(), List.of(), List.of());
    }

    public record ChannelHit(
            Long id,
            String name,
            String slug,
            String description,
            String ownerType,
            String ownerSlug
    ) {
    }

    public record UserHit(
            Long id,
            String username,
            String nickname,
            String avatar
    ) {
    }

    public record ConversationHit(
            String id,
            String title,
            String updateTime
    ) {
    }
}
