package bao.gabriel.cognia.system.dto.profile;

import java.util.List;

public record OwnerProfileResponse(
        String type,
        String id,
        String username,
        String nickname,
        String avatar,
        String gender,
        String bio,
        String company,
        String hometown,
        String timezone,
        String email,
        String readme,
        List<ProfileLink> links,
        List<String> badges,
        List<OwnerTeamRef> teams,
        List<ChannelCard> channels,
        List<ChannelCard> pinnedChannels,
        long memberCount,
        String viewerRole,
        boolean self,
        boolean canEdit,
        String createTime
) {
}
