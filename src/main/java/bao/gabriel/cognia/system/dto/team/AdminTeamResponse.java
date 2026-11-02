package bao.gabriel.cognia.system.dto.team;

/**
 * Team row shown in the console team management table.
 */
public record AdminTeamResponse(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String ownerId,
        String ownerName,
        String ownerUsername,
        long memberCount,
        long channelCount,
        String createTime
) {
}
