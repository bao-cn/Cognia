package bao.gabriel.cognia.system.dto.team;

import java.util.List;

public record TeamDetailResponse(
        String id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String readme,
        List<TeamLinkResponse> links,
        boolean joinRequestsEnabled,
        String defaultMemberRole,
        TeamMemberResponse owner,
        long memberCount,
        List<TeamMemberResponse> members,
        List<TeamChannelResponse> channels,
        boolean member,
        boolean isOwner,
        String viewerRole,
        boolean canManageSettings,
        boolean canCreateChannel,
        boolean canPinChannel,
        boolean canDeleteAnyChannel,
        String createTime
) {
}
