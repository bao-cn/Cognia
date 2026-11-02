package bao.gabriel.cognia.system.dto.team;

public record TeamSummaryResponse(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String ownerId,
        String ownerName,
        long memberCount,
        String role,
        String joinedAt,
        String createTime
) {
}
