package bao.gabriel.cognia.system.dto.profile;

public record OwnerTeamRef(
        String id,
        String name,
        String slug,
        String logoUrl,
        String role,
        long memberCount
) {
}
