package bao.gabriel.cognia.system.dto.team;

public record TeamLimitsResponse(
        int createdCount,
        int creationLimit,
        int joinedCount,
        int joinLimit
) {
}
