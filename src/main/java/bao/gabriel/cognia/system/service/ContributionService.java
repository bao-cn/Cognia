package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.contribution.UserContributionResponse;

public interface ContributionService {

    void record(Long userId, String eventType, Long channelId, String channelName,
                Long teamId, String teamName, int amount, String metadata);

    UserContributionResponse summary(String username, Long viewerId, Integer year, Long beforeId, Integer limit);

    void updateVisibility(Long userId, boolean publicVisible);
}
