package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.Role;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.RoleMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertRecipientService {

    private final ChannelMapper channelMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    /** Emails of the channel owner/administrators. */
    public List<String> channelAdminEmails(Long channelId) {
        if (channelId == null) return List.of();
        Channel channel = channelMapper.selectById(channelId);
        if (channel == null) return List.of();
        Set<String> emails = new LinkedHashSet<>();
        if (Channel.OWNER_USER.equalsIgnoreCase(channel.getOwnerType())) {
            addUserEmail(emails, channel.getOwnerId());
        } else if (Channel.OWNER_TEAM.equalsIgnoreCase(channel.getOwnerType())) {
            Team team = teamMapper.selectById(channel.getOwnerId());
            if (team != null) {
                addUserEmail(emails, team.getOwnerId());
                List<TeamMember> managers = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, team.getId())
                        .in(TeamMember::getRole, "OWNER", "MANAGER"));
                for (TeamMember member : managers) addUserEmail(emails, member.getUserId());
            }
        }
        return new ArrayList<>(emails);
    }

    /** Operations alert recipients: explicit address, otherwise all users with the configured role. */
    public List<String> opsEmails(String roleCode, String explicitRecipient) {
        if (StringUtils.hasText(explicitRecipient)) {
            return List.of(explicitRecipient.trim());
        }
        String code = StringUtils.hasText(roleCode) ? roleCode.trim() : "OPS";
        Role role = roleMapper.selectByCode(code);
        if (role == null) return List.of();
        Set<String> emails = new LinkedHashSet<>();
        for (Long userId : userRoleMapper.selectUserIdsByRoleId(role.getId())) {
            addUserEmail(emails, userId);
        }
        return new ArrayList<>(emails);
    }

    private void addUserEmail(Set<String> emails, Long userId) {
        if (userId == null) return;
        User user = userMapper.selectById(userId);
        if (user != null && user.isEnabled() && StringUtils.hasText(user.getEmail())) {
            emails.add(user.getEmail().trim());
        }
    }
}
