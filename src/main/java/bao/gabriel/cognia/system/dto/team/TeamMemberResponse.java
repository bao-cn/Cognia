package bao.gabriel.cognia.system.dto.team;

public record TeamMemberResponse(
        String userId,
        String username,
        String nickname,
        String avatar,
        String role,
        String joinedAt
) {
}
