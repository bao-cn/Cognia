package bao.gabriel.cognia.system.dto.contribution;

public record ContributionActivityResponse(
        Long id,
        String eventType,
        Long channelId,
        String channelName,
        Long teamId,
        String teamName,
        int amount,
        String createTime
) {
}
