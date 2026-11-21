package bao.gabriel.cognia.system.dto.contribution;

import java.util.List;

public record UserContributionResponse(
        boolean publicVisible,
        boolean canEdit,
        int year,
        long total,
        long channelCount,
        List<ContributionDailyResponse> daily,
        List<ContributionTeamResponse> teams,
        List<String> topChannels,
        ContributionRadarResponse radar,
        List<ContributionActivityResponse> activities,
        boolean hasMore,
        Long nextBefore
) {
}
