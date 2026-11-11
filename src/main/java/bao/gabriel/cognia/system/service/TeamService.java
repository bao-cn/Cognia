package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.team.CreateTeamRequest;
import bao.gabriel.cognia.system.dto.team.TeamChannelRequest;
import bao.gabriel.cognia.system.dto.team.TeamChannelResponse;
import bao.gabriel.cognia.system.dto.team.TeamDetailResponse;
import bao.gabriel.cognia.system.dto.team.TeamInvitationResponse;
import bao.gabriel.cognia.system.dto.team.TeamLimitsResponse;
import bao.gabriel.cognia.system.dto.team.TeamSummaryResponse;
import bao.gabriel.cognia.system.dto.team.TeamUserSearchResponse;
import bao.gabriel.cognia.system.dto.team.UpdateTeamRequest;

import java.util.List;

public interface TeamService {

    List<TeamSummaryResponse> myTeams(Long userId);

    TeamDetailResponse getTeam(String id, Long userId);

    TeamDetailResponse createTeam(Long userId, CreateTeamRequest request);

    TeamLimitsResponse getLimits(Long userId);

    List<TeamInvitationResponse> incomingInvitations(Long userId);

    List<TeamInvitationResponse> teamInvitations(Long userId, Long teamId);

    List<TeamUserSearchResponse> searchInvitees(Long userId, Long teamId, String keyword);

    TeamInvitationResponse invite(Long userId, Long teamId, Long inviteeId);

    TeamDetailResponse acceptInvitation(Long userId, Long invitationId);

    TeamInvitationResponse declineInvitation(Long userId, Long invitationId);

    TeamDetailResponse updateReadme(Long userId, Long teamId, String readme);

    TeamDetailResponse updateMemberRole(Long userId, Long teamId, Long targetUserId, String role);

    TeamChannelResponse createChannel(Long userId, Long teamId, TeamChannelRequest request);

    TeamChannelResponse updateChannel(Long userId, Long teamId, Long channelId, TeamChannelRequest request);

    void deleteChannel(Long userId, Long teamId, Long channelId);

    TeamChannelResponse pinChannel(Long userId, Long teamId, Long channelId, boolean pinned);

    TeamDetailResponse updateTeam(Long userId, Long teamId, UpdateTeamRequest request);

    String updateLogo(Long userId, Long teamId, org.springframework.web.multipart.MultipartFile file);

    void deleteTeam(Long userId, Long teamId);

    TeamDetailResponse transferOwnership(Long userId, Long teamId, Long targetUserId);

    void removeMember(Long userId, Long teamId, Long targetUserId);
}
