package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.util.ReservedNames;
import bao.gabriel.cognia.system.dto.team.CreateTeamRequest;
import bao.gabriel.cognia.system.dto.team.TeamInvitationResponse;
import bao.gabriel.cognia.system.dto.team.TeamChannelRequest;
import bao.gabriel.cognia.system.dto.team.TeamChannelResponse;
import bao.gabriel.cognia.system.dto.team.TeamDetailResponse;
import bao.gabriel.cognia.system.dto.team.TeamLimitsResponse;
import bao.gabriel.cognia.system.dto.team.TeamMemberResponse;
import bao.gabriel.cognia.system.dto.team.TeamSummaryResponse;
import bao.gabriel.cognia.system.dto.team.TeamUserSearchResponse;
import bao.gabriel.cognia.system.dto.team.TeamLinkResponse;
import bao.gabriel.cognia.system.dto.team.TeamLinkRequest;
import bao.gabriel.cognia.system.dto.team.UpdateTeamRequest;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamChannel;
import bao.gabriel.cognia.system.entity.TeamInvitation;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.TeamInvitationMapper;
import bao.gabriel.cognia.system.mapper.TeamChannelMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.TeamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final String DEFAULT_LOGO = "/logo.png";

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamChannelMapper channelMapper;
    private final TeamInvitationMapper invitationMapper;
    private final UserMapper userMapper;
    private final SiteSettingService settingService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<TeamSummaryResponse> myTeams(Long userId) {
        List<TeamMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .orderByDesc(TeamMember::getJoinedAt));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, Team> teams = teamMapper.selectBatchIds(
                        memberships.stream().map(TeamMember::getTeamId).toList())
                .stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, User> users = usersByIds(teams.values().stream().map(Team::getOwnerId).toList());
        return memberships.stream()
                .map(member -> {
                    Team team = teams.get(member.getTeamId());
                    return team == null ? null : toSummary(team, member, users.get(team.getOwnerId()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public TeamDetailResponse getTeam(String id, Long userId) {
        Team team = requireTeamById(id);
        return toDetail(team, userId);
    }

    @Override
    @Transactional
    public TeamDetailResponse createTeam(Long userId, CreateTeamRequest request) {
        int creationLimit = settingService.getSetting().teamCreationLimit();
        long createdCount = teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                .eq(Team::getOwnerId, userId));
        if (createdCount >= creationLimit) {
            throw BusinessException.badRequest("最多只能创建 " + creationLimit + " 个团队");
        }

        String name = request.name().trim();
        String slug = uniqueSlug(request.slug(), name);
        String logoUrl = normalizeLogo(request.logoUrl());
        LocalDateTime now = LocalDateTime.now();

        Team team = new Team();
        team.setName(name);
        team.setSlug(slug);
        team.setDescription(trimToNull(request.description()));
        team.setLogoUrl(logoUrl);
        team.setReadme("# " + name + "\n\n欢迎来到 " + name + "。在这里记录团队的目标、协作方式和知识规范。");
        team.setJoinRequestsEnabled(false);
        team.setDefaultMemberRole(TeamMember.ROLE_MEMBER);
        team.setOwnerId(userId);
        team.setCreateTime(now);
        team.setUpdateTime(now);
        teamMapper.insert(team);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole(TeamMember.ROLE_OWNER);
        member.setJoinedAt(now);
        memberMapper.insert(member);
        log.info("User {} created team {} ({})", userId, team.getName(), team.getSlug());
        return toDetail(team, userId);
    }

    @Override
    public List<TeamInvitationResponse> incomingInvitations(Long userId) {
        return invitationMapper.selectList(new LambdaQueryWrapper<TeamInvitation>()
                        .eq(TeamInvitation::getInviteeId, userId)
                        .eq(TeamInvitation::getStatus, TeamInvitation.STATUS_PENDING)
                        .orderByDesc(TeamInvitation::getCreateTime))
                .stream()
                .map(this::toInvitation)
                .toList();
    }

    @Override
    public List<TeamInvitationResponse> teamInvitations(Long userId, Long teamId) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        return invitationMapper.selectList(new LambdaQueryWrapper<TeamInvitation>()
                        .eq(TeamInvitation::getTeamId, teamId)
                        .eq(TeamInvitation::getStatus, TeamInvitation.STATUS_PENDING)
                        .orderByDesc(TeamInvitation::getCreateTime))
                .stream()
                .map(this::toInvitation)
                .toList();
    }

    @Override
    public List<TeamUserSearchResponse> searchInvitees(Long userId, Long teamId, String keyword) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .orderByAsc(User::getUsername)
                .last("LIMIT 20");
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query.like(User::getUsername, value)
                    .or().like(User::getNickname, value));
        }
        return userMapper.selectList(wrapper).stream()
                .map(user -> new TeamUserSearchResponse(
                        String.valueOf(user.getId()),
                        user.getUsername(),
                        displayName(user),
                        StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : DEFAULT_LOGO,
                        findMembership(team.getId(), user.getId()) != null,
                        invitationMapper.selectCount(new LambdaQueryWrapper<TeamInvitation>()
                                .eq(TeamInvitation::getTeamId, team.getId())
                                .eq(TeamInvitation::getInviteeId, user.getId())
                                .eq(TeamInvitation::getStatus, TeamInvitation.STATUS_PENDING)) > 0
                ))
                .toList();
    }

    @Override
    @Transactional
    public TeamInvitationResponse invite(Long userId, Long teamId, Long inviteeId) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        if (inviteeId == null || inviteeId.equals(userId)) {
            throw BusinessException.badRequest("不能邀请自己");
        }
        User invitee = userMapper.selectById(inviteeId);
        if (invitee == null || !invitee.isEnabled()) {
            throw BusinessException.notFound("用户不存在或已被禁用");
        }
        if (findMembership(team.getId(), inviteeId) != null) {
            throw BusinessException.conflict("该用户已经是团队成员");
        }
        long pending = invitationMapper.selectCount(new LambdaQueryWrapper<TeamInvitation>()
                .eq(TeamInvitation::getTeamId, team.getId())
                .eq(TeamInvitation::getInviteeId, inviteeId)
                .eq(TeamInvitation::getStatus, TeamInvitation.STATUS_PENDING));
        if (pending > 0) {
            throw BusinessException.conflict("已经向该用户发送过邀请");
        }
        LocalDateTime now = LocalDateTime.now();
        TeamInvitation invitation = new TeamInvitation();
        invitation.setTeamId(team.getId());
        invitation.setInviterId(userId);
        invitation.setInviteeId(inviteeId);
        invitation.setStatus(TeamInvitation.STATUS_PENDING);
        invitation.setCreateTime(now);
        invitation.setUpdateTime(now);
        invitationMapper.insert(invitation);
        log.info("User {} invited {} to team {}", userId, inviteeId, team.getId());
        return toInvitation(invitation);
    }

    @Override
    @Transactional
    public TeamDetailResponse acceptInvitation(Long userId, Long invitationId) {
        TeamInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getInviteeId().equals(userId)) {
            throw BusinessException.notFound("邀请不存在");
        }
        if (!TeamInvitation.STATUS_PENDING.equals(invitation.getStatus())) {
            throw BusinessException.badRequest("该邀请已处理");
        }
        Team team = requireTeamById(String.valueOf(invitation.getTeamId()));
        if (findMembership(team.getId(), userId) != null) {
            invitation.setStatus(TeamInvitation.STATUS_ACCEPTED);
            invitation.setUpdateTime(LocalDateTime.now());
            invitationMapper.updateById(invitation);
            return toDetail(team, userId);
        }
        int joinLimit = settingService.getSetting().teamJoinLimit();
        long joinedCount = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .ne(TeamMember::getRole, TeamMember.ROLE_OWNER));
        if (joinedCount >= joinLimit) {
            throw BusinessException.badRequest("最多只能加入 " + joinLimit + " 个团队");
        }
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole(normalizeDefaultRole(team.getDefaultMemberRole()));
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);
        invitation.setStatus(TeamInvitation.STATUS_ACCEPTED);
        invitation.setUpdateTime(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        log.info("User {} accepted invitation to team {}", userId, team.getId());
        return toDetail(team, userId);
    }

    @Override
    @Transactional
    public TeamInvitationResponse declineInvitation(Long userId, Long invitationId) {
        TeamInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getInviteeId().equals(userId)) {
            throw BusinessException.notFound("邀请不存在");
        }
        if (!TeamInvitation.STATUS_PENDING.equals(invitation.getStatus())) {
            throw BusinessException.badRequest("该邀请已处理");
        }
        invitation.setStatus(TeamInvitation.STATUS_DECLINED);
        invitation.setUpdateTime(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        return toInvitation(invitation);
    }

    @Override
    public TeamLimitsResponse getLimits(Long userId) {
        int creationLimit = settingService.getSetting().teamCreationLimit();
        int joinLimit = settingService.getSetting().teamJoinLimit();
        int createdCount = Math.toIntExact(teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                .eq(Team::getOwnerId, userId)));
        int joinedCount = Math.toIntExact(memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .ne(TeamMember::getRole, TeamMember.ROLE_OWNER)));
        return new TeamLimitsResponse(createdCount, creationLimit, joinedCount, joinLimit);
    }

    private TeamDetailResponse toDetail(Team team, Long userId) {
        List<TeamMember> members = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId())
                .orderByAsc(TeamMember::getJoinedAt)
                .orderByAsc(TeamMember::getId));
        Map<Long, User> users = usersByIds(members.stream().map(TeamMember::getUserId).toList());
        List<TeamMemberResponse> memberResponses = members.stream()
                .map(member -> toMember(member, users.get(member.getUserId())))
                .toList();
        TeamMember self = members.stream().filter(member -> member.getUserId().equals(userId)).findFirst().orElse(null);
        User owner = users.get(team.getOwnerId());
        if (owner == null) {
            owner = userMapper.selectById(team.getOwnerId());
        }
        String viewerRole = self == null ? null : self.getRole();
        boolean isMember = self != null;
        boolean isOwner = TeamMember.ROLE_OWNER.equals(viewerRole);
        boolean canManageSettings = isOwner;
        boolean canCreateChannel = isOwner
                || TeamMember.ROLE_MANAGER.equals(viewerRole)
                || TeamMember.ROLE_EDITOR.equals(viewerRole);
        boolean canPinChannel = isOwner || TeamMember.ROLE_MANAGER.equals(viewerRole);
        boolean canDeleteAnyChannel = canPinChannel;

        LambdaQueryWrapper<TeamChannel> channelQuery = new LambdaQueryWrapper<TeamChannel>()
                .eq(TeamChannel::getTeamId, team.getId())
                .orderByDesc(TeamChannel::getPinned)
                .orderByAsc(TeamChannel::getPinnedOrder)
                .orderByAsc(TeamChannel::getCreateTime);
        if (!isMember) {
            channelQuery.eq(TeamChannel::getIsPublic, true);
        }
        List<TeamChannel> channels = channelMapper.selectList(channelQuery);
        Map<Long, User> channelCreators = usersByIds(channels.stream().map(TeamChannel::getCreatedBy).toList());
        List<TeamChannelResponse> channelResponses = channels.stream()
                .map(channel -> toChannel(channel, channelCreators.get(channel.getCreatedBy())))
                .toList();

        return new TeamDetailResponse(
                String.valueOf(team.getId()),
                team.getName(),
                team.getSlug(),
                team.getDescription(),
                team.getLogoUrl(),
                team.getReadme(),
                parseLinks(team.getCustomLinks()),
                Boolean.TRUE.equals(team.getJoinRequestsEnabled()),
                normalizeDefaultRole(team.getDefaultMemberRole()),
                owner == null ? null : toMember(
                        members.stream().filter(member -> member.getUserId().equals(team.getOwnerId())).findFirst().orElse(null),
                        owner),
                members.size(),
                memberResponses,
                channelResponses,
                isMember,
                isOwner,
                viewerRole,
                canManageSettings,
                canCreateChannel,
                canPinChannel,
                canDeleteAnyChannel,
                settingService.formatDateTime(team.getCreateTime())
        );
    }

    private TeamSummaryResponse toSummary(Team team, TeamMember membership, User owner) {
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId()));
        return new TeamSummaryResponse(
                String.valueOf(team.getId()),
                team.getName(),
                team.getSlug(),
                team.getDescription(),
                team.getLogoUrl(),
                String.valueOf(team.getOwnerId()),
                owner == null ? "未知用户" : displayName(owner),
                memberCount,
                membership == null ? null : membership.getRole(),
                membership == null ? null : settingService.formatDateTime(membership.getJoinedAt()),
                settingService.formatDateTime(team.getCreateTime())
        );
    }

    private TeamMemberResponse toMember(TeamMember member, User user) {
        if (member == null || user == null) {
            return new TeamMemberResponse(
                    member == null ? null : String.valueOf(member.getUserId()),
                    "unknown",
                    "未知用户",
                    DEFAULT_LOGO,
                    member == null ? TeamMember.ROLE_MEMBER : member.getRole(),
                    member == null ? null : settingService.formatDateTime(member.getJoinedAt())
            );
        }
        return new TeamMemberResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                displayName(user),
                StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : DEFAULT_LOGO,
                member.getRole(),
                settingService.formatDateTime(member.getJoinedAt())
        );
    }

    private void requireOwner(Team team, Long userId) {
        if (!team.getOwnerId().equals(userId)) {
            throw BusinessException.badRequest("只有团队创建者可以执行此操作");
        }
    }

    private TeamInvitation requireInvitation(Long invitationId) {
        if (invitationId == null) {
            throw BusinessException.badRequest("邀请 ID 不能为空");
        }
        TeamInvitation invitation = invitationMapper.selectById(invitationId);
        if (invitation == null) {
            throw BusinessException.notFound("邀请不存在");
        }
        return invitation;
    }

    private TeamInvitationResponse toInvitation(TeamInvitation invitation) {
        Team team = teamMapper.selectById(invitation.getTeamId());
        User inviter = userMapper.selectById(invitation.getInviterId());
        User invitee = userMapper.selectById(invitation.getInviteeId());
        return new TeamInvitationResponse(
                String.valueOf(invitation.getId()),
                String.valueOf(invitation.getTeamId()),
                team == null ? "已删除的团队" : team.getName(),
                team == null ? null : team.getDescription(),
                team == null ? DEFAULT_LOGO : team.getLogoUrl(),
                String.valueOf(invitation.getInviterId()),
                inviter == null ? "未知用户" : displayName(inviter),
                String.valueOf(invitation.getInviteeId()),
                invitee == null ? "未知用户" : displayName(invitee),
                invitation.getStatus(),
                settingService.formatDateTime(invitation.getCreateTime())
        );
    }

    @Override
    @Transactional
    public TeamDetailResponse updateReadme(Long userId, Long teamId, String readme) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        team.setReadme(readme == null || readme.isBlank() ? null : readme);
        team.setUpdateTime(LocalDateTime.now());
        teamMapper.updateById(team);
        return toDetail(team, userId);
    }

    @Override
    @Transactional
    public TeamDetailResponse updateMemberRole(Long userId, Long teamId, Long targetUserId, String role) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        TeamMember target = findMembership(team.getId(), targetUserId);
        if (target == null) {
            throw BusinessException.notFound("成员不存在");
        }
        if (TeamMember.ROLE_OWNER.equals(target.getRole())) {
            throw BusinessException.badRequest("不能修改团队创建者的角色");
        }
        if (!TeamMember.ROLE_MANAGER.equals(role)
                && !TeamMember.ROLE_EDITOR.equals(role)
                && !TeamMember.ROLE_MEMBER.equals(role)) {
            throw BusinessException.badRequest("成员角色只能为 MANAGER、EDITOR 或 MEMBER");
        }
        target.setRole(role);
        memberMapper.updateById(target);
        return toDetail(team, userId);
    }

    @Override
    @Transactional
    public TeamChannelResponse createChannel(Long userId, Long teamId, TeamChannelRequest request) {
        Team team = requireTeamById(String.valueOf(teamId));
        TeamMember member = requireMember(team, userId);
        if (!canCreateChannel(member)) {
            throw BusinessException.badRequest("你没有创建频道的权限");
        }
        TeamChannel channel = new TeamChannel();
        channel.setTeamId(team.getId());
        channel.setName(request.name().trim());
        channel.setDescription(trimToNull(request.description()));
        channel.setIsPublic(request.isPublic() == null || request.isPublic());
        channel.setPinned(false);
        channel.setPinnedOrder(0);
        channel.setCreatedBy(userId);
        channel.setCreateTime(LocalDateTime.now());
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.insert(channel);
        return toChannel(channel, userMapper.selectById(userId));
    }

    @Override
    @Transactional
    public TeamChannelResponse updateChannel(Long userId, Long teamId, Long channelId, TeamChannelRequest request) {
        Team team = requireTeamById(String.valueOf(teamId));
        TeamMember member = requireMember(team, userId);
        if (!canCreateChannel(member)) {
            throw BusinessException.badRequest("你没有修改频道的权限");
        }
        TeamChannel channel = requireChannel(team.getId(), channelId);
        channel.setName(request.name().trim());
        channel.setDescription(trimToNull(request.description()));
        if (request.isPublic() != null) {
            channel.setIsPublic(request.isPublic());
        }
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toChannel(channel, userMapper.selectById(channel.getCreatedBy()));
    }

    @Override
    @Transactional
    public void deleteChannel(Long userId, Long teamId, Long channelId) {
        Team team = requireTeamById(String.valueOf(teamId));
        TeamMember member = requireMember(team, userId);
        TeamChannel channel = requireChannel(team.getId(), channelId);
        boolean canDelete = canDeleteAnyChannel(member)
                || (TeamMember.ROLE_EDITOR.equals(member.getRole()) && channel.getCreatedBy().equals(userId));
        if (!canDelete) {
            throw BusinessException.badRequest("你没有删除该频道的权限");
        }
        channelMapper.deleteById(channelId);
    }

    @Override
    @Transactional
    public TeamChannelResponse pinChannel(Long userId, Long teamId, Long channelId, boolean pinned) {
        Team team = requireTeamById(String.valueOf(teamId));
        TeamMember member = requireMember(team, userId);
        if (!canPinChannel(member)) {
            throw BusinessException.badRequest("你没有置顶频道的权限");
        }
        TeamChannel channel = requireChannel(team.getId(), channelId);
        if (pinned && !Boolean.TRUE.equals(channel.getPinned())) {
            long pinnedCount = channelMapper.selectCount(new LambdaQueryWrapper<TeamChannel>()
                    .eq(TeamChannel::getTeamId, team.getId())
                    .eq(TeamChannel::getPinned, true));
            if (pinnedCount >= 6) {
                throw BusinessException.badRequest("最多只能置顶 6 个频道");
            }
            channel.setPinned(true);
            channel.setPinnedOrder((int) pinnedCount + 1);
        } else if (!pinned) {
            channel.setPinned(false);
            channel.setPinnedOrder(0);
        }
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toChannel(channel, userMapper.selectById(channel.getCreatedBy()));
    }

    @Override
    @Transactional
    public TeamDetailResponse updateTeam(Long userId, Long teamId, UpdateTeamRequest request) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        team.setName(request.name().trim());
        team.setDescription(trimToNull(request.description()));
        if (request.logoUrl() != null) {
            team.setLogoUrl(normalizeLogo(request.logoUrl()));
        }
        List<TeamLinkRequest> links = request.links() == null ? List.of() : request.links();
        team.setCustomLinks(writeLinks(links));
        if (request.joinRequestsEnabled() != null) {
            team.setJoinRequestsEnabled(request.joinRequestsEnabled());
        }
        team.setDefaultMemberRole(normalizeDefaultRole(request.defaultMemberRole()));
        team.setUpdateTime(LocalDateTime.now());
        teamMapper.updateById(team);
        return toDetail(team, userId);
    }

    @Override
    @Transactional
    public void deleteTeam(Long userId, Long teamId) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        channelMapper.delete(new LambdaQueryWrapper<TeamChannel>().eq(TeamChannel::getTeamId, teamId));
        invitationMapper.delete(new LambdaQueryWrapper<TeamInvitation>().eq(TeamInvitation::getTeamId, teamId));
        memberMapper.delete(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        // Keep a soft-deleted team row: the scheduled deleted-owner cleanup
        // needs its id to remove kb/{teamId}/ objects and pgvector chunks.
        teamMapper.softDelete(teamId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public TeamDetailResponse transferOwnership(Long userId, Long teamId, Long targetUserId) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw BusinessException.badRequest("请选择其他团队成员");
        }
        TeamMember target = findMembership(team.getId(), targetUserId);
        if (target == null) {
            throw BusinessException.notFound("成员不存在");
        }
        target.setRole(TeamMember.ROLE_OWNER);
        memberMapper.updateById(target);
        TeamMember current = findMembership(team.getId(), userId);
        if (current != null) {
            current.setRole(TeamMember.ROLE_MANAGER);
            memberMapper.updateById(current);
        }
        team.setOwnerId(targetUserId);
        team.setUpdateTime(LocalDateTime.now());
        teamMapper.updateById(team);
        return toDetail(team, userId);
    }

    @Override
    @Transactional
    public void removeMember(Long userId, Long teamId, Long targetUserId) {
        Team team = requireTeamById(String.valueOf(teamId));
        requireOwner(team, userId);
        if (targetUserId == null || targetUserId.equals(userId) || targetUserId.equals(team.getOwnerId())) {
            throw BusinessException.badRequest("不能移除团队创建者");
        }
        TeamMember target = findMembership(team.getId(), targetUserId);
        if (target == null) {
            throw BusinessException.notFound("成员不存在");
        }
        memberMapper.deleteById(target.getId());
    }

    private List<TeamLinkResponse> parseLinks(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            List<TeamLinkRequest> links = OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
            return links.stream().map(link -> new TeamLinkResponse(link.label(), link.url())).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeLinks(List<TeamLinkRequest> links) {
        List<TeamLinkRequest> normalized = links == null ? List.of() : links.stream()
                .map(link -> new TeamLinkRequest(link.label().trim(), link.url().trim()))
                .toList();
        for (TeamLinkRequest link : normalized) {
            String url = link.url();
            if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))) {
                throw BusinessException.badRequest("自定义链接必须是站内路径或 HTTP(S) 地址");
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw BusinessException.badRequest("自定义链接格式不正确");
        }
    }

    private String normalizeDefaultRole(String role) {
        if (TeamMember.ROLE_MANAGER.equals(role) || TeamMember.ROLE_EDITOR.equals(role)) {
            return role;
        }
        return TeamMember.ROLE_MEMBER;
    }

    private TeamChannel requireChannel(Long teamId, Long channelId) {
        if (channelId == null) {
            throw BusinessException.badRequest("频道 ID 不能为空");
        }
        TeamChannel channel = channelMapper.selectById(channelId);
        if (channel == null || !channel.getTeamId().equals(teamId)) {
            throw BusinessException.notFound("频道不存在");
        }
        return channel;
    }

    private TeamMember requireMember(Team team, Long userId) {
        TeamMember member = findMembership(team.getId(), userId);
        if (member == null) {
            throw BusinessException.notFound("团队不存在或你不在该团队中");
        }
        return member;
    }

    private boolean canCreateChannel(TeamMember member) {
        return TeamMember.ROLE_OWNER.equals(member.getRole())
                || TeamMember.ROLE_MANAGER.equals(member.getRole())
                || TeamMember.ROLE_EDITOR.equals(member.getRole());
    }

    private boolean canPinChannel(TeamMember member) {
        return TeamMember.ROLE_OWNER.equals(member.getRole())
                || TeamMember.ROLE_MANAGER.equals(member.getRole());
    }

    private boolean canDeleteAnyChannel(TeamMember member) {
        return canPinChannel(member);
    }

    private TeamChannelResponse toChannel(TeamChannel channel, User creator) {
        return new TeamChannelResponse(
                String.valueOf(channel.getId()),
                channel.getName(),
                channel.getDescription(),
                Boolean.TRUE.equals(channel.getIsPublic()),
                Boolean.TRUE.equals(channel.getPinned()),
                channel.getPinnedOrder() == null ? 0 : channel.getPinnedOrder(),
                String.valueOf(channel.getCreatedBy()),
                creator == null ? "未知用户" : displayName(creator),
                settingService.formatDateTime(channel.getCreateTime())
        );
    }

    private Team requireTeamById(String id) {
        if (!StringUtils.hasText(id)) {
            throw BusinessException.badRequest("团队 ID 不能为空");
        }
        long teamId;
        try {
            teamId = Long.parseLong(id.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.notFound("团队不存在");
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BusinessException.notFound("团队不存在");
        }
        return team;
    }

    private TeamMember findMembership(Long teamId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private Map<Long, User> usersByIds(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> result = new LinkedHashMap<>();
        for (User user : userMapper.selectBatchIds(distinct)) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private String uniqueSlug(String requested, String name) {
        boolean explicit = StringUtils.hasText(requested);
        String base = slugify(explicit ? requested : name);
        if (ReservedNames.isReserved(base)) {
            throw BusinessException.badRequest("团队标识「" + base + "」是系统保留字，请更换");
        }
        long teamCount = teamMapper.countBySlugIncludingDeleted(base);
        long userCount = userMapper.countByUsernameIncludingDeleted(base);
        if (teamCount > 0 || userCount > 0) {
            if (explicit) {
                throw BusinessException.conflict("团队标识已被占用，请更换");
            }
            throw BusinessException.conflict("团队名称生成的标识已被占用，请手动填写团队标识");
        }
        return base;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.length() < 2) {
            normalized = "team";
        }
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48).replaceAll("-+$", "");
        }
        return normalized;
    }

    private String normalizeLogo(String logoUrl) {
        if (!StringUtils.hasText(logoUrl)) {
            return DEFAULT_LOGO;
        }
        String value = logoUrl.trim();
        boolean allowed = value.startsWith("/")
                || value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("data:image/webp;base64,");
        if (!allowed) {
            throw BusinessException.badRequest("团队 Logo 必须是站内路径、HTTP(S) 地址或 WebP 图片");
        }
        if (value.length() > 200000) {
            throw BusinessException.badRequest("团队 Logo 数据过大");
        }
        return value;
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
