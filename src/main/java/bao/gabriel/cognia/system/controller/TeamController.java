package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.team.CreateTeamRequest;
import bao.gabriel.cognia.system.dto.team.InviteTeamMemberRequest;
import bao.gabriel.cognia.system.dto.team.PinChannelRequest;
import bao.gabriel.cognia.system.dto.team.TeamChannelRequest;
import bao.gabriel.cognia.system.dto.team.TeamChannelResponse;
import bao.gabriel.cognia.system.dto.team.TeamDetailResponse;
import bao.gabriel.cognia.system.dto.team.TeamInvitationResponse;
import bao.gabriel.cognia.system.dto.team.TeamLimitsResponse;
import bao.gabriel.cognia.system.dto.team.TeamSummaryResponse;
import bao.gabriel.cognia.system.dto.team.TransferTeamRequest;
import bao.gabriel.cognia.system.dto.team.UpdateTeamRequest;
import bao.gabriel.cognia.system.dto.team.UpdateTeamMemberRoleRequest;
import bao.gabriel.cognia.system.dto.team.UpdateTeamReadmeRequest;
import bao.gabriel.cognia.system.dto.team.TeamUserSearchResponse;
import bao.gabriel.cognia.system.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/mine")
    public ApiResponse<List<TeamSummaryResponse>> mine() {
        return ApiResponse.success(teamService.myTeams(SecurityUtils.getUserId()));
    }

    @GetMapping("/limits")
    public ApiResponse<TeamLimitsResponse> limits() {
        return ApiResponse.success(teamService.getLimits(SecurityUtils.getUserId()));
    }

    @GetMapping("/invitations")
    public ApiResponse<List<TeamInvitationResponse>> incomingInvitations() {
        return ApiResponse.success(teamService.incomingInvitations(SecurityUtils.getUserId()));
    }

    @PostMapping("/invitations/{id}/accept")
    public ApiResponse<TeamDetailResponse> acceptInvitation(@PathVariable Long id) {
        return ApiResponse.success(teamService.acceptInvitation(SecurityUtils.getUserId(), id));
    }

    @PostMapping("/invitations/{id}/decline")
    public ApiResponse<TeamInvitationResponse> declineInvitation(@PathVariable Long id) {
        return ApiResponse.success(teamService.declineInvitation(SecurityUtils.getUserId(), id));
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.success(teamService.getTeam(id, SecurityUtils.getUserId()));
    }

    @PostMapping
    public ApiResponse<TeamDetailResponse> create(@Valid @RequestBody CreateTeamRequest request) {
        return ApiResponse.success(teamService.createTeam(SecurityUtils.getUserId(), request));
    }

    @GetMapping("/{id}/invitations")
    public ApiResponse<List<TeamInvitationResponse>> teamInvitations(@PathVariable Long id) {
        return ApiResponse.success(teamService.teamInvitations(SecurityUtils.getUserId(), id));
    }

    @GetMapping("/{id}/invitees")
    public ApiResponse<List<TeamUserSearchResponse>> invitees(@PathVariable Long id,
                                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.success(teamService.searchInvitees(SecurityUtils.getUserId(), id, keyword));
    }

    @PostMapping("/{id}/invitations")
    public ApiResponse<TeamInvitationResponse> invite(@PathVariable Long id,
                                                      @Valid @RequestBody InviteTeamMemberRequest request) {
        return ApiResponse.success(teamService.invite(SecurityUtils.getUserId(), id, request.userId()));
    }

    @PutMapping("/{id}/readme")
    public ApiResponse<TeamDetailResponse> updateReadme(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateTeamReadmeRequest request) {
        return ApiResponse.success(teamService.updateReadme(SecurityUtils.getUserId(), id, request.readme()));
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ApiResponse<TeamDetailResponse> updateMemberRole(@PathVariable Long id,
                                                            @PathVariable Long userId,
                                                            @Valid @RequestBody UpdateTeamMemberRoleRequest request) {
        return ApiResponse.success(teamService.updateMemberRole(
                SecurityUtils.getUserId(), id, userId, request.role()));
    }

    @PostMapping("/{id}/channels")
    public ApiResponse<TeamChannelResponse> createChannel(@PathVariable Long id,
                                                          @Valid @RequestBody TeamChannelRequest request) {
        return ApiResponse.success(teamService.createChannel(SecurityUtils.getUserId(), id, request));
    }

    @PutMapping("/{id}/channels/{channelId}")
    public ApiResponse<TeamChannelResponse> updateChannel(@PathVariable Long id,
                                                          @PathVariable Long channelId,
                                                          @Valid @RequestBody TeamChannelRequest request) {
        return ApiResponse.success(teamService.updateChannel(SecurityUtils.getUserId(), id, channelId, request));
    }

    @DeleteMapping("/{id}/channels/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannel(@PathVariable Long id, @PathVariable Long channelId) {
        teamService.deleteChannel(SecurityUtils.getUserId(), id, channelId);
    }

    @PostMapping("/{id}/channels/{channelId}/pin")
    public ApiResponse<TeamChannelResponse> pinChannel(@PathVariable Long id,
                                                       @PathVariable Long channelId,
                                                       @Valid @RequestBody PinChannelRequest request) {
        return ApiResponse.success(teamService.pinChannel(
                SecurityUtils.getUserId(), id, channelId, Boolean.TRUE.equals(request.pinned())));
    }

    @PutMapping("/{id}")
    public ApiResponse<TeamDetailResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateTeamRequest request) {
        return ApiResponse.success(teamService.updateTeam(SecurityUtils.getUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(SecurityUtils.getUserId(), id);
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<TeamDetailResponse> transfer(@PathVariable Long id,
                                                    @Valid @RequestBody TransferTeamRequest request) {
        return ApiResponse.success(teamService.transferOwnership(SecurityUtils.getUserId(), id, request.userId()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMember(SecurityUtils.getUserId(), id, userId);
    }
}
