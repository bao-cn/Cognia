package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.contribution.ContributionActivityResponse;
import bao.gabriel.cognia.system.dto.contribution.ContributionDailyResponse;
import bao.gabriel.cognia.system.dto.contribution.ContributionRadarResponse;
import bao.gabriel.cognia.system.dto.contribution.ContributionTeamResponse;
import bao.gabriel.cognia.system.dto.contribution.UserContributionResponse;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.entity.UserContribution;
import bao.gabriel.cognia.system.mapper.UserContributionMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.ContributionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContributionServiceImpl implements ContributionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserContributionMapper contributionMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void record(Long userId, String eventType, Long channelId, String channelName,
                       Long teamId, String teamName, int amount, String metadata) {
        if (userId == null || eventType == null) {
            return;
        }
        UserContribution event = new UserContribution();
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setChannelId(channelId);
        event.setChannelName(channelName);
        event.setTeamId(teamId);
        event.setTeamName(teamName);
        event.setAmount(Math.max(1, amount));
        event.setMetadata(metadata);
        event.setCreateTime(LocalDateTime.now());
        contributionMapper.insert(event);
    }

    @Override
    @Transactional(readOnly = true)
    public UserContributionResponse summary(String username, Long viewerId, Integer year, Long beforeId, Integer limit) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getIsDeleted, false));
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        boolean canEdit = viewerId != null && viewerId.equals(user.getId());
        boolean publicVisible = user.getContributionsPublic() == null || Boolean.TRUE.equals(user.getContributionsPublic());
        if (!canEdit && !publicVisible) {
            throw BusinessException.forbidden("该用户未公开贡献统计");
        }

        int selectedYear = year == null ? Year.now().getValue() : Math.max(2000, Math.min(2100, year));
        LocalDateTime yearStart = LocalDate.of(selectedYear, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = yearStart.plusYears(1);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM sys_user_contribution WHERE user_id = ? AND create_time >= ? AND create_time < ?",
                Long.class, user.getId(), Timestamp.valueOf(yearStart), Timestamp.valueOf(yearEnd));

        List<ContributionDailyResponse> daily = jdbcTemplate.query(
                "SELECT to_char(create_time, 'YYYY-MM-DD') AS d, SUM(amount) AS c FROM sys_user_contribution " +
                        "WHERE user_id = ? AND create_time >= ? AND create_time < ? GROUP BY 1 ORDER BY 1",
                (rs, rowNum) -> new ContributionDailyResponse(rs.getString("d"), rs.getLong("c")),
                user.getId(), Timestamp.valueOf(yearStart), Timestamp.valueOf(yearEnd));

        List<ContributionTeamResponse> teams = jdbcTemplate.query(
                "SELECT c.team_id, MAX(c.team_name) AS team_name, MAX(t.slug) AS team_slug, SUM(c.amount) AS cnt " +
                        "FROM sys_user_contribution c LEFT JOIN sys_team t ON t.id = c.team_id " +
                        "WHERE c.user_id = ? AND c.team_id IS NOT NULL AND c.create_time >= ? AND c.create_time < ? " +
                        "GROUP BY c.team_id ORDER BY cnt DESC",
                (rs, rowNum) -> new ContributionTeamResponse(
                        rs.getLong("team_id"), rs.getString("team_name"), rs.getString("team_slug"), rs.getLong("cnt")),
                user.getId(), Timestamp.valueOf(yearStart), Timestamp.valueOf(yearEnd));

        List<Map<String, Object>> channelRows = jdbcTemplate.queryForList(
                "SELECT MAX(channel_name) AS channel_name, SUM(amount) AS c FROM sys_user_contribution " +
                        "WHERE user_id = ? AND channel_name IS NOT NULL AND create_time >= ? AND create_time < ? " +
                        "GROUP BY channel_id ORDER BY c DESC",
                user.getId(), Timestamp.valueOf(yearStart), Timestamp.valueOf(yearEnd));
        List<String> topChannels = new ArrayList<>();
        for (int i = 0; i < Math.min(3, channelRows.size()); i++) {
            Object name = channelRows.get(i).get("channel_name");
            if (name != null) {
                topChannels.add(String.valueOf(name));
            }
        }
        long channelCount = channelRows.size();

        Map<String, Long> radarCounts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT event_type, SUM(amount) AS c FROM sys_user_contribution " +
                        "WHERE user_id = ? AND create_time >= ? AND create_time < ? GROUP BY event_type",
                rs -> { radarCounts.put(rs.getString("event_type"), rs.getLong("c")); },
                user.getId(), Timestamp.valueOf(yearStart), Timestamp.valueOf(yearEnd));
        ContributionRadarResponse radar = new ContributionRadarResponse(
                radarCounts.getOrDefault("DOC_CREATE", 0L),
                radarCounts.getOrDefault("DOC_DELETE", 0L),
                radarCounts.getOrDefault("DOC_UPDATE", 0L),
                radarCounts.getOrDefault("TICKET_REPLY", 0L));

        int pageSize = limit == null ? 12 : Math.max(1, Math.min(50, limit));
        LocalDateTime timelineStart = LocalDateTime.now().minusMonths(3);
        StringBuilder sql = new StringBuilder(
                "SELECT id, event_type, channel_id, channel_name, team_id, team_name, amount, create_time " +
                        "FROM sys_user_contribution WHERE user_id = ? AND create_time >= ?");
        List<Object> args = new ArrayList<>();
        args.add(user.getId());
        args.add(Timestamp.valueOf(timelineStart));
        if (beforeId != null) {
            sql.append(" AND id < ?");
            args.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(pageSize + 1);
        List<ContributionActivityResponse> activities = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new ContributionActivityResponse(
                        rs.getLong("id"), rs.getString("event_type"), (Long) rs.getObject("channel_id"),
                        rs.getString("channel_name"), (Long) rs.getObject("team_id"), rs.getString("team_name"),
                        rs.getInt("amount"), rs.getTimestamp("create_time").toLocalDateTime().format(TIME)),
                args.toArray());
        boolean hasMore = activities.size() > pageSize;
        if (hasMore) {
            activities = new ArrayList<>(activities.subList(0, pageSize));
        }
        Long nextBefore = activities.isEmpty() ? null : activities.get(activities.size() - 1).id();

        return new UserContributionResponse(publicVisible, canEdit, selectedYear, total == null ? 0 : total,
                channelCount, daily, teams, topChannels, radar, activities, hasMore, nextBefore);
    }

    @Override
    public void updateVisibility(Long userId, boolean publicVisible) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        user.setContributionsPublic(publicVisible);
        userMapper.updateById(user);
    }
}
