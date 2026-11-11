package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.system.dto.dashboard.AdminDashboardResponse;
import bao.gabriel.cognia.system.dto.dashboard.AdminDashboardResponse.*;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.dashboard.ChannelInsightsResponse;
import bao.gabriel.cognia.system.dto.dashboard.ChannelInsightsResponse.TopDocument;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.service.AdminDashboardService;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.NotificationSseService;
import bao.gabriel.cognia.system.service.ObjectStorageService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final NotificationSseService sseService;
    private final ObjectStorageService objectStorageService;
    private final SiteSettingService siteSettingService;
    private final ChannelService channelService;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    public AdminDashboardResponse overview() {
        Map<String, Object> users = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE status = 1) AS active,
                       count(*) FILTER (WHERE status <> 1) AS banned,
                       count(*) FILTER (WHERE last_login_time IS NULL OR last_login_time < now() - interval '30 days') AS inactive,
                       count(*) FILTER (WHERE create_time >= current_date) AS today,
                       count(*) FILTER (WHERE create_time >= now() - interval '7 days') AS week,
                       count(*) FILTER (WHERE create_time >= now() - interval '30 days') AS month
                FROM sys_user WHERE is_deleted = FALSE
                """);
        Map<String, Object> channels = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE is_public) AS public_count,
                       count(*) FILTER (WHERE NOT is_public) AS private_count
                FROM kb_channel
                """);
        long files = longValue(jdbc.queryForObject("""
                SELECT count(*) FROM kb_document WHERE status <> 'DELETED'
                """, Long.class));
        long storageBytes = longValue(jdbc.queryForObject("""
                SELECT COALESCE(sum(size), 0) FROM kb_document WHERE status <> 'DELETED'
                """, Long.class));
        long conversations = longValue(jdbc.queryForObject("SELECT count(*) FROM chat_conversation", Long.class));
        long messages = longValue(jdbc.queryForObject("SELECT count(*) FROM chat_message", Long.class));
        double avgRounds = conversations == 0 ? 0 : Math.round((double) messages / conversations * 10d) / 10d;

        Map<String, Object> qa = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       COALESCE(sum(CASE WHEN retrieval_used THEN 1 ELSE 0 END), 0) AS retrieval,
                       COALESCE(sum(llm_rounds), 0) AS llm,
                       count(*) FILTER (WHERE create_time >= current_date) AS today,
                       count(*) FILTER (WHERE create_time >= now() - interval '7 days') AS week,
                       count(*) FILTER (WHERE create_time >= now() - interval '30 days') AS month,
                       COALESCE(avg(CASE WHEN retrieval_used THEN CASE WHEN retrieval_hit THEN 1.0 ELSE 0.0 END END), 0) AS hit_rate,
                       COALESCE(avg(CASE WHEN retrieval_used THEN 0.0 ELSE 1.0 END), 0) AS no_retrieval_rate,
                       COALESCE(avg(CASE WHEN refused THEN 1.0 ELSE 0.0 END), 0) AS refusal_rate
                FROM sys_metric_event WHERE event_type = 'QA'
                """);
        Map<String, Object> latency = jdbc.queryForMap("""
                SELECT COALESCE(avg(retrieval_duration_ms) FILTER (WHERE retrieval_used), 0) AS avg_retrieval,
                       COALESCE(avg(rerank_duration_ms) FILTER (WHERE rerank_used), 0) AS avg_rerank,
                       COALESCE(avg(duration_ms), 0) AS avg_qa,
                       COALESCE(percentile_disc(0.5) WITHIN GROUP (ORDER BY duration_ms), 0) AS p50,
                       COALESCE(percentile_disc(0.9) WITHIN GROUP (ORDER BY duration_ms), 0) AS p90,
                       COALESCE(percentile_disc(0.99) WITHIN GROUP (ORDER BY duration_ms), 0) AS p99
                FROM sys_metric_event WHERE event_type = 'QA' AND status = 'SUCCESS'
                """);
        long retained = longValue(jdbc.queryForObject("""
                SELECT count(DISTINCT c.user_id) FROM chat_conversation c
                WHERE c.create_time >= now() - interval '7 days'
                  AND EXISTS (SELECT 1 FROM chat_conversation p WHERE p.user_id = c.user_id
                              AND p.create_time BETWEEN now() - interval '14 days' AND now() - interval '7 days')
                """, Long.class));
        long recent = longValue(jdbc.queryForObject("""
                SELECT count(DISTINCT user_id) FROM chat_conversation WHERE create_time >= now() - interval '7 days'
                """, Long.class));

        List<TrendPoint> trends = trends();
        List<FileTypeStat> fileTypes = jdbc.query("""
                SELECT COALESCE(NULLIF(lower(substring(name from '\\.([^.]+)$')), ''), 'other') AS type,
                       count(*) AS count, COALESCE(sum(size), 0) AS bytes
                FROM kb_document WHERE status <> 'DELETED'
                GROUP BY 1 ORDER BY count DESC LIMIT 12
                """, (rs, i) -> new FileTypeStat(rs.getString("type"), rs.getLong("count"), rs.getLong("bytes")));

        List<ModelStat> models = jdbc.query("""
                SELECT COALESCE(model_name, 'unknown') AS model_name, COALESCE(sum(llm_rounds), 0) AS calls,
                       COALESCE(sum(input_tokens), 0) AS input_tokens,
                       COALESCE(sum(output_tokens), 0) AS output_tokens,
                       count(*) FILTER (WHERE status = 'FAILED') AS errors,
                       COALESCE(avg(duration_ms), 0) AS avg_duration
                FROM sys_metric_event WHERE event_type = 'QA'
                GROUP BY 1 ORDER BY calls DESC LIMIT 12
                """, (rs, i) -> new ModelStat(rs.getString("model_name"), rs.getLong("calls"),
                rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("errors"),
                rs.getLong("avg_duration")));
        List<TokenStat> tokensByUser = jdbc.query("""
                SELECT COALESCE(u.nickname, u.username, '未知用户') AS name,
                       COALESCE(sum(m.input_tokens), 0) AS input_tokens,
                       COALESCE(sum(m.output_tokens), 0) AS output_tokens,
                       COALESCE(sum(m.embedding_tokens), 0) AS embedding_tokens
                FROM sys_metric_event m LEFT JOIN sys_user u ON u.id = m.user_id
                WHERE m.event_type = 'QA' GROUP BY 1 ORDER BY (sum(m.input_tokens) + sum(m.output_tokens)) DESC LIMIT 10
                """, (rs, i) -> new TokenStat(rs.getString("name"), rs.getLong("input_tokens"),
                rs.getLong("output_tokens"), rs.getLong("embedding_tokens")));
        List<TokenStat> tokensByModel = jdbc.query("""
                SELECT COALESCE(model_name, 'unknown') AS name,
                       COALESCE(sum(input_tokens), 0) AS input_tokens,
                       COALESCE(sum(output_tokens), 0) AS output_tokens,
                       COALESCE(sum(embedding_tokens), 0) AS embedding_tokens
                FROM sys_metric_event WHERE event_type = 'QA' GROUP BY 1 ORDER BY input_tokens DESC LIMIT 10
                """, (rs, i) -> new TokenStat(rs.getString("name"), rs.getLong("input_tokens"),
                rs.getLong("output_tokens"), rs.getLong("embedding_tokens")));

        List<ErrorCodeStat> errorCodes = jdbc.query("""
                SELECT COALESCE(error_type, 'UNKNOWN') AS error_type, count(*) AS count
                FROM sys_metric_event WHERE event_type = 'QA' AND status = 'FAILED'
                GROUP BY 1 ORDER BY count DESC
                """, (rs, i) -> new ErrorCodeStat(rs.getString("error_type"), rs.getLong("count")));
        long errorsToday = queryLong("""
                SELECT count(*) FROM sys_metric_event WHERE event_type = 'QA' AND status = 'FAILED'
                  AND create_time >= current_date
                """);

        Ops ops = ops(errorsToday);
        List<TaskStat> tasks = jdbc.query("""
                SELECT code, name, enabled, last_status, last_run_at, last_message
                FROM sys_scheduled_task ORDER BY id LIMIT 20
                """, (rs, i) -> new TaskStat(rs.getString("code"), rs.getString("name"),
                rs.getBoolean("enabled"), rs.getString("last_status"),
                rs.getTimestamp("last_run_at") == null ? null : rs.getTimestamp("last_run_at").toLocalDateTime().format(TIME),
                rs.getString("last_message")));

        Overview overview = new Overview(
                longValue(users.get("total")), longValue(users.get("active")), longValue(users.get("banned")),
                longValue(users.get("inactive")), longValue(users.get("today")), longValue(users.get("week")),
                longValue(users.get("month")), longValue(channels.get("total")), longValue(channels.get("public_count")),
                longValue(channels.get("private_count")), files, storageBytes, conversations, avgRounds,
                longValue(qa.get("total")), longValue(qa.get("retrieval")), longValue(qa.get("llm")),
                longValue(qa.get("today")), longValue(qa.get("week")), longValue(qa.get("month")),
                round(qa.get("hit_rate")), round(qa.get("no_retrieval_rate")), round(qa.get("refusal_rate")),
                recent == 0 ? 0 : Math.round((double) retained / recent * 1000d) / 10d);
        Latency lat = new Latency(longValue(latency.get("avg_retrieval")), 0,
                longValue(latency.get("avg_rerank")), longValue(latency.get("avg_qa")),
                longValue(latency.get("p50")), longValue(latency.get("p90")), longValue(latency.get("p99")));
        return new AdminDashboardResponse(overview, lat, trends, fileTypes, models, tokensByUser, tokensByModel,
                ops, tasks, errorCodes, LocalDateTime.now().format(TIME));
    }

    private List<TrendPoint> trends() {
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            byDate.put(LocalDateTime.now().minusDays(i).format(DATE), new long[]{0, 0, 0});
        }
        jdbc.query("""
                SELECT to_char(create_time, 'YYYY-MM-DD') AS day, count(*) AS qa,
                       COALESCE(sum(CASE WHEN retrieval_used THEN 1 ELSE 0 END), 0) AS retrieval,
                       COALESCE(sum(llm_rounds), 0) AS llm
                FROM sys_metric_event WHERE event_type = 'QA' AND create_time >= now() - interval '14 days'
                GROUP BY 1 ORDER BY 1
                """, rs -> {
            long[] values = byDate.get(rs.getString("day"));
            if (values != null) {
                values[0] = rs.getLong("qa");
                values[1] = rs.getLong("retrieval");
                values[2] = rs.getLong("llm");
            }
        });
        List<TrendPoint> result = new ArrayList<>();
        byDate.forEach((date, values) -> result.add(new TrendPoint(date, values[0], values[1], values[2])));
        return result;
    }

    private Ops ops(long errorsToday) {
        long dbConnections = queryLong("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()");
        long slowQueries = queryLong("""
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND state = 'active'
                  AND now() - query_start > interval '2 seconds'
                """);
        long vectorBytes = queryLong("SELECT COALESCE(pg_total_relation_size('vector_store'), 0)");
        long docQueue = queryLong("SELECT count(*) FROM kb_document WHERE status IN ('PENDING','PROCESSING')");
        long scheduledEnabled = queryLong("SELECT count(*) FROM sys_scheduled_task WHERE enabled");
        long scheduledFailed = queryLong("SELECT count(*) FROM sys_scheduled_task WHERE last_status = 'FAILED'");
        String redisMemory = "-";
        double redisHitRate = 0;
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            String memory = connection.info("memory").getProperty("used_memory_human", "-");
            redisMemory = memory;
            var stats = connection.info("stats");
            long hits = parseLong(stats.getProperty("keyspace_hits"));
            long misses = parseLong(stats.getProperty("keyspace_misses"));
            redisHitRate = hits + misses == 0 ? 0 : Math.round((double) hits / (hits + misses) * 1000d) / 10d;
        } catch (Exception ignored) {
            // Redis is optional for the dashboard.
        }
        long minioBytes = queryLong("SELECT COALESCE(sum(size), 0) FROM kb_document WHERE status <> 'DELETED'");
        long minioObjects = queryLong("SELECT count(*) FROM kb_document WHERE status <> 'DELETED'");
        int lifecycleDays = 7;
        try {
            lifecycleDays = objectStorageService.getAdminSettings().tempRetentionDays();
        } catch (Exception ignored) {
        }
        List<String> warnings = new ArrayList<>();
        if (slowQueries > 0) warnings.add("存在 " + slowQueries + " 条慢查询");
        if (errorsToday > 0) warnings.add("今日有 " + errorsToday + " 次问答失败");
        if (scheduledFailed > 0) warnings.add(scheduledFailed + " 个计划任务最近失败");
        if (docQueue > 20) warnings.add("文档处理队列积压 " + docQueue);
        return new Ops("UP", "UP", sseService.onlineCount(), docQueue == 0 ? "IDLE" : "RUNNING",
                docQueue, docQueue == 0 ? "IDLE" : "RUNNING", docQueue,
                scheduledFailed == 0 ? "UP" : "WARN", scheduledEnabled, scheduledFailed,
                dbConnections, vectorBytes, slowQueries, redisMemory, redisHitRate,
                minioBytes, minioObjects, lifecycleDays, false, 0, 0, scheduledFailed, 0,
                warnings);
    }

    @Override
    public ChannelInsightsResponse channelInsights(Long channelId, Long viewerId) {
        var detail = channelService.detail(channelId, viewerId);
        if (!detail.canViewInsights()) {
            throw BusinessException.forbidden("仅频道成员可以查看 Insights");
        }
        var channel = detail.channel();
        Map<String, Object> docs = jdbc.queryForMap("""
                SELECT count(*) AS count, COALESCE(sum(size), 0) AS bytes
                FROM kb_document WHERE channel_id = ? AND status <> 'DELETED'
                """, channelId);
        long subscribers = queryLong("SELECT count(*) FROM kb_channel_subscription WHERE channel_id = ?", channelId);
        long ownerId = 0L;
        try { ownerId = Long.parseLong(channel.ownerId()); } catch (Exception ignored) { }
        long members = "TEAM".equalsIgnoreCase(channel.ownerType())
                ? teamMemberMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<bao.gabriel.cognia.system.entity.TeamMember>()
                    .eq(bao.gabriel.cognia.system.entity.TeamMember::getTeamId, ownerId)) : 1;
        Map<String, Object> qa = jdbc.queryForMap("""
                SELECT count(*) AS total,
                       COALESCE(sum(CASE WHEN retrieval_used THEN 1 ELSE 0 END), 0) AS retrieval,
                       COALESCE(sum(input_tokens), 0) AS input_tokens,
                       COALESCE(sum(output_tokens), 0) AS output_tokens,
                       COALESCE(avg(CASE WHEN retrieval_used THEN CASE WHEN retrieval_hit THEN 1.0 ELSE 0.0 END END), 0) AS hit_rate,
                       COALESCE(avg(CASE WHEN refused THEN 1.0 ELSE 0.0 END), 0) AS refusal_rate,
                       COALESCE(avg(duration_ms), 0) AS avg_latency,
                       COALESCE(percentile_disc(0.9) WITHIN GROUP (ORDER BY duration_ms), 0) AS p90
                FROM sys_metric_event WHERE event_type = 'QA' AND channel_id = ?
                """, channelId);
        List<ChannelInsightsResponse.FileTypeStat> types = jdbc.query("""
                SELECT COALESCE(NULLIF(lower(substring(name from '\\.([^.]+)$')), ''), 'other') AS type,
                       count(*) AS count, COALESCE(sum(size), 0) AS bytes
                FROM kb_document WHERE channel_id = ? AND status <> 'DELETED'
                GROUP BY 1 ORDER BY count DESC
                """, (rs, i) -> new ChannelInsightsResponse.FileTypeStat(rs.getString("type"), rs.getLong("count"), rs.getLong("bytes")), channelId);
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) byDate.put(LocalDateTime.now().minusDays(i).format(DATE), new long[]{0, 0, 0});
        jdbc.query("""
                SELECT to_char(create_time, 'YYYY-MM-DD') AS day, count(*) AS qa,
                       COALESCE(sum(CASE WHEN retrieval_used THEN 1 ELSE 0 END), 0) AS retrieval,
                       COALESCE(sum(llm_rounds), 0) AS llm
                FROM sys_metric_event WHERE event_type = 'QA' AND channel_id = ?
                  AND create_time >= now() - interval '14 days'
                GROUP BY 1 ORDER BY 1
                """, rs -> {
            long[] values = byDate.get(rs.getString("day"));
            if (values != null) { values[0] = rs.getLong("qa"); values[1] = rs.getLong("retrieval"); values[2] = rs.getLong("llm"); }
        }, channelId);
        List<ChannelInsightsResponse.TrendPoint> trends = new ArrayList<>();
        byDate.forEach((date, v) -> trends.add(new ChannelInsightsResponse.TrendPoint(date, v[0], v[1], v[2])));
        List<TopDocument> top = jdbc.query("""
                SELECT id, name, COALESCE(content_type, '') AS content_type, size, status, create_time
                FROM kb_document WHERE channel_id = ? AND status <> 'DELETED'
                ORDER BY create_time DESC LIMIT 8
                """, (rs, i) -> new TopDocument(rs.getLong("id"), rs.getString("name"),
                rs.getString("content_type"), rs.getLong("size"), rs.getString("status"),
                rs.getTimestamp("create_time") == null ? "" : rs.getTimestamp("create_time").toLocalDateTime().format(TIME)),
                channelId);
        return new ChannelInsightsResponse(
                new ChannelInsightsResponse.ChannelSummary(
                        channel.id() == null ? null : Long.parseLong(channel.id()),
                        channel.name(), channel.slug(), channel.isPublic(), members),
                longValue(docs.get("count")), longValue(docs.get("bytes")), subscribers,
                longValue(qa.get("total")), longValue(qa.get("retrieval")),
                longValue(qa.get("input_tokens")), longValue(qa.get("output_tokens")),
                round(qa.get("hit_rate")), round(qa.get("refusal_rate")),
                longValue(qa.get("avg_latency")), longValue(qa.get("p90")),
                types, trends, top, LocalDateTime.now().format(TIME));
    }

    private long queryLong(String sql, Object... args) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class, args);
            return longValue(value);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ex) { return 0; }
    }

    private static double round(Object value) {
        double number = value instanceof Number n ? n.doubleValue() : 0;
        return Math.round(number * 1000d) / 10d;
    }
}
