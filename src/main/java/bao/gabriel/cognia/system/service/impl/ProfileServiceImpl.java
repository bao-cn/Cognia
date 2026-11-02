package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.profile.ChannelCard;
import bao.gabriel.cognia.system.dto.profile.OwnerProfileResponse;
import bao.gabriel.cognia.system.dto.profile.OwnerTeamRef;
import bao.gabriel.cognia.system.dto.profile.ProfileLink;
import bao.gabriel.cognia.system.dto.profile.UpdateProfileRequest;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.ProfileService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_LINKS = 4;

    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final ChannelMapper channelMapper;
    private final KbDocumentMapper documentMapper;
    private final SiteSettingService siteSettingService;

    @Override
    public OwnerProfileResponse getProfile(String identifier, Long viewerId) {
        if (!StringUtils.hasText(identifier)) {
            throw BusinessException.badRequest("请输入用户或团队标识");
        }
        String value = identifier.trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, value)
                .last("LIMIT 1"));
        if (user != null) {
            return userProfile(user, viewerId);
        }
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getSlug, value.toLowerCase(Locale.ROOT))
                .last("LIMIT 1"));
        if (team != null) {
            return teamProfile(team, viewerId);
        }
        throw BusinessException.notFound("用户或团队不存在");
    }

    @Override
    public OwnerProfileResponse getMyProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return userProfile(user, userId);
    }

    @Override
    @Transactional
    public OwnerProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            user.setNickname(nickname.isEmpty() ? user.getUsername() : nickname);
        }
        if (request.gender() != null) {
            user.setGender(trimToNull(request.gender()));
        }
        if (request.bio() != null) {
            user.setBio(trimToNull(request.bio()));
        }
        if (request.company() != null) {
            user.setCompany(trimToNull(request.company()));
        }
        if (request.hometown() != null) {
            user.setHometown(trimToNull(request.hometown()));
        }
        if (request.timezone() != null) {
            String timezone = trimToNull(request.timezone());
            if (timezone != null) {
                try {
                    ZoneId.of(timezone);
                } catch (Exception ex) {
                    throw BusinessException.badRequest("时区格式不正确");
                }
            }
            user.setTimezone(timezone);
        }
        if (request.readme() != null) {
            user.setReadme(trimToNull(request.readme()));
        }
        if (request.links() != null) {
            user.setSocialLinks(writeLinks(request.links()));
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return userProfile(user, userId);
    }

    private OwnerProfileResponse userProfile(User user, Long viewerId) {
        boolean self = viewerId != null && viewerId.equals(user.getId());
        List<ChannelCard> channels = channelCards(Channel.OWNER_USER, user.getId(), self);
        List<ChannelCard> pinned = channels.stream()
                .filter(ChannelCard::pinned)
                .sorted(Comparator.comparingInt(ChannelCard::pinnedOrder))
                .limit(4)
                .toList();
        List<OwnerTeamRef> teams = userTeams(user.getId());
        return new OwnerProfileResponse(
                "USER", String.valueOf(user.getId()), user.getUsername(), user.getNickname(),
                user.getAvatar(), user.getGender(), user.getBio(), user.getCompany(),
                user.getHometown(), user.getTimezone(), self ? user.getEmail() : null,
                user.getReadme(), readLinks(user.getSocialLinks()), List.of(), teams,
                channels, pinned, 0, self ? "OWNER" : null, self, self,
                siteSettingService.formatDateTime(user.getCreateTime()));
    }

    private OwnerProfileResponse teamProfile(Team team, Long viewerId) {
        String viewerRole = null;
        if (viewerId != null) {
            TeamMember membership = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, team.getId())
                    .eq(TeamMember::getUserId, viewerId)
                    .last("LIMIT 1"));
            if (membership != null) {
                viewerRole = membership.getRole();
            }
        }
        boolean member = viewerRole != null;
        List<ChannelCard> channels = channelCards(Channel.OWNER_TEAM, team.getId(), member);
        List<ChannelCard> pinned = channels.stream()
                .filter(ChannelCard::pinned)
                .sorted(Comparator.comparingInt(ChannelCard::pinnedOrder))
                .limit(4)
                .toList();
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId()));
        return new OwnerProfileResponse(
                "TEAM", String.valueOf(team.getId()), team.getSlug(), team.getName(),
                team.getLogoUrl(), null, team.getDescription(), null, null, null, null,
                team.getReadme(), readLinks(team.getCustomLinks()), List.of(), List.of(),
                channels, pinned, memberCount, viewerRole, false,
                TeamMember.ROLE_OWNER.equals(viewerRole),
                siteSettingService.formatDateTime(team.getCreateTime()));
    }

    private List<OwnerTeamRef> userTeams(Long userId) {
        List<TeamMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, String> roles = new LinkedHashMap<>();
        for (TeamMember membership : memberships) {
            roles.put(membership.getTeamId(), membership.getRole());
        }
        List<Team> teams = teamMapper.selectBatchIds(roles.keySet());
        List<OwnerTeamRef> result = new ArrayList<>();
        for (Team team : teams) {
            long count = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, team.getId()));
            result.add(new OwnerTeamRef(String.valueOf(team.getId()), team.getName(), team.getSlug(),
                    team.getLogoUrl(), roles.get(team.getId()), count));
        }
        return result;
    }

    private List<ChannelCard> channelCards(String ownerType, Long ownerId, boolean includePrivate) {
        List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getOwnerType, ownerType)
                .eq(Channel::getOwnerId, ownerId)
                .eq(!includePrivate, Channel::getIsPublic, true)
                .orderByDesc(Channel::getPinned)
                .orderByAsc(Channel::getPinnedOrder)
                .orderByDesc(Channel::getUpdateTime));
        List<ChannelCard> cards = new ArrayList<>();
        for (Channel channel : channels) {
            long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                    .eq(KbDocument::getChannelId, channel.getId())
                    .ne(KbDocument::getStatus, "DELETED"));
            cards.add(new ChannelCard(String.valueOf(channel.getId()), channel.getName(),
                    channel.getSlug(), channel.getDescription(), channel.isPublic(),
                    Boolean.TRUE.equals(channel.getPinned()),
                    channel.getPinnedOrder() == null ? 0 : channel.getPinnedOrder(),
                    documentCount,
                    siteSettingService.formatDateTime(channel.getUpdateTime())));
        }
        return cards;
    }

    private List<ProfileLink> readLinks(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<ProfileLink>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeLinks(List<ProfileLink> links) {
        if (links.size() > MAX_LINKS) {
            throw BusinessException.badRequest("自定义链接最多 4 个");
        }
        List<ProfileLink> normalized = new ArrayList<>();
        for (ProfileLink link : links) {
            String label = link.label() == null ? "" : link.label().trim();
            String url = link.url() == null ? "" : link.url().trim();
            if (label.isEmpty() || url.isEmpty()) {
                continue;
            }
            validateHttpUrl(url);
            normalized.add(new ProfileLink(label, url));
        }
        try {
            return normalized.isEmpty() ? null : JSON.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw BusinessException.badRequest("自定义链接格式不正确");
        }
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw BusinessException.badRequest("自定义链接必须是 http(s) 地址");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
