package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.team.AdminTeamResponse;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamChannel;
import bao.gabriel.cognia.system.entity.TeamInvitation;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.TeamChannelMapper;
import bao.gabriel.cognia.system.mapper.TeamInvitationMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.AdminTeamService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTeamServiceImpl implements AdminTeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamChannelMapper legacyChannelMapper;
    private final TeamInvitationMapper invitationMapper;
    private final ChannelMapper channelMapper;
    private final UserMapper userMapper;
    private final SiteSettingService siteSettingService;

    @Override
    public PageResult<AdminTeamResponse> page(long page, long size, String keyword) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<Team>()
                .orderByDesc(Team::getCreateTime)
                .orderByDesc(Team::getId);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query.like(Team::getName, value)
                    .or()
                    .like(Team::getSlug, value));
        }
        Page<Team> result = teamMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<AdminTeamResponse> records = new ArrayList<>();
        for (Team team : result.getRecords()) {
            records.add(toResponse(team));
        }
        return PageResult.of(records, result.getTotal(), safePage, safeSize);
    }

    @Override
    @Transactional
    public void delete(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BusinessException.notFound("团队不存在");
        }
        // The soft-deleted team row is intentionally kept: the scheduled
        // deleted-owner cleanup uses its id to remove kb/{teamId}/ objects.
        channelMapper.delete(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getOwnerType, Channel.OWNER_TEAM)
                .eq(Channel::getOwnerId, teamId));
        legacyChannelMapper.delete(new LambdaQueryWrapper<TeamChannel>()
                .eq(TeamChannel::getTeamId, teamId));
        invitationMapper.delete(new LambdaQueryWrapper<TeamInvitation>()
                .eq(TeamInvitation::getTeamId, teamId));
        memberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));
        teamMapper.softDelete(teamId, LocalDateTime.now());
    }

    private AdminTeamResponse toResponse(Team team) {
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId()));
        long channelCount = channelMapper.selectCount(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getOwnerType, Channel.OWNER_TEAM)
                .eq(Channel::getOwnerId, team.getId()));
        User owner = team.getOwnerId() == null ? null : userMapper.selectById(team.getOwnerId());
        String ownerName = owner == null ? "" : (StringUtils.hasText(owner.getNickname())
                ? owner.getNickname() : owner.getUsername());
        return new AdminTeamResponse(
                String.valueOf(team.getId()),
                team.getName(),
                team.getSlug(),
                team.getDescription(),
                team.getLogoUrl(),
                team.getOwnerId() == null ? "" : String.valueOf(team.getOwnerId()),
                ownerName,
                owner == null ? "" : owner.getUsername(),
                memberCount,
                channelCount,
                team.getCreateTime() == null ? "" : siteSettingService.formatDateTime(team.getCreateTime())
        );
    }
}
