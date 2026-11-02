package bao.gabriel.cognia.system.dto.team;

public record TeamUserSearchResponse(
        String id,
        String username,
        String nickname,
        String avatar,
        boolean member,
        boolean invited
) {
}
