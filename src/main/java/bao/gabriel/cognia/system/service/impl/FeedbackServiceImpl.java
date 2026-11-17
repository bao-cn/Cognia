package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.feedback.FeedbackDetailResponse;
import bao.gabriel.cognia.system.dto.feedback.FeedbackResponse;
import bao.gabriel.cognia.system.entity.ChatFeedback;
import bao.gabriel.cognia.system.mapper.ChatFeedbackMapper;
import bao.gabriel.cognia.system.service.FeedbackService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ChatFeedbackMapper feedbackMapper;
    private final JdbcTemplate jdbc;

    @Override
    public FeedbackResponse submit(Long userId, Long messageId, String type, String reason) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (!"LIKE".equals(normalized) && !"DISLIKE".equals(normalized)) {
            throw BusinessException.badRequest("反馈类型只能是 LIKE 或 DISLIKE");
        }
        List<Long> owners = jdbc.queryForList("""
                SELECT c.user_id FROM chat_message m JOIN chat_conversation c ON c.id = m.conversation_id
                WHERE m.id = ? AND c.user_id = ?
                """, Long.class, messageId, userId);
        if (owners.isEmpty()) {
            throw BusinessException.notFound("消息不存在");
        }
        Long conversationId = jdbc.queryForObject("SELECT conversation_id FROM chat_message WHERE id = ?",
                Long.class, messageId);
        String traceId = jdbc.query("SELECT trace_id FROM chat_trace WHERE message_id = ? ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, messageId);
        ChatFeedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<ChatFeedback>()
                .eq(ChatFeedback::getMessageId, messageId));
        LocalDateTime now = LocalDateTime.now();
        ChatFeedback feedback = existing == null ? new ChatFeedback() : existing;
        feedback.setMessageId(messageId);
        feedback.setConversationId(conversationId);
        feedback.setUserId(userId);
        feedback.setFeedbackType(normalized);
        feedback.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        feedback.setStatus(existing == null ? "PENDING" : "PENDING");
        feedback.setTraceId(traceId);
        feedback.setCreateTime(existing == null ? now : existing.getCreateTime());
        feedback.setUpdateTime(now);
        if (existing == null) {
            feedbackMapper.insert(feedback);
        } else {
            feedbackMapper.updateById(feedback);
        }
        return toResponse(feedback);
    }

    @Override
    public PageResult<FeedbackResponse> page(long page, long size, String type, String status) {
        long p = Math.max(1, page);
        long s = Math.min(100, Math.max(1, size));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(type)) {
            where.append(" AND f.feedback_type = ?");
            args.add(type.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND f.status = ?");
            args.add(status.trim().toUpperCase());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM chat_feedback f" + where, Long.class, args.toArray());
        args.add(s);
        args.add((p - 1) * s);
        List<FeedbackResponse> records = jdbc.query("""
                SELECT f.*, u.username, u.nickname, t.question, m.content AS answer
                FROM chat_feedback f
                LEFT JOIN sys_user u ON u.id = f.user_id
                LEFT JOIN chat_message m ON m.id = f.message_id
                LEFT JOIN chat_trace t ON t.trace_id = f.trace_id
                """ + where + " ORDER BY f.create_time DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapRow(rs), args.toArray());
        long pages = total == null || total == 0 ? 0 : (total + s - 1) / s;
        return new PageResult<>(records, total == null ? 0 : total, p, s, pages);
    }

    @Override
    public FeedbackDetailResponse detail(Long id) {
        ChatFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw BusinessException.notFound("反馈不存在");
        }
        FeedbackResponse response = toResponse(feedback, true);
        List<Map<String, Object>> messages = jdbc.query("""
                SELECT id, role, content, reasoning, create_time FROM chat_message
                WHERE conversation_id = ? ORDER BY id ASC
                """, (rs, rowNum) -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", String.valueOf(rs.getLong("id")));
            item.put("role", rs.getString("role"));
            item.put("content", rs.getString("content"));
            item.put("reasoning", rs.getString("reasoning"));
            item.put("createTime", rs.getTimestamp("create_time") == null ? null
                    : rs.getTimestamp("create_time").toLocalDateTime().format(TIME));
            return item;
        }, feedback.getConversationId());
        String traceJson = null;
        if (StringUtils.hasText(feedback.getTraceId())) {
            List<String> traces = jdbc.queryForList("SELECT trace_json FROM chat_trace WHERE trace_id = ?",
                    String.class, feedback.getTraceId());
            traceJson = traces.isEmpty() ? null : traces.get(0);
        }
        return new FeedbackDetailResponse(response, messages, traceJson);
    }

    @Override
    public FeedbackResponse update(Long id, String problemType, String status, String note) {
        ChatFeedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw BusinessException.notFound("反馈不存在");
        }
        if (StringUtils.hasText(problemType)) feedback.setProblemType(problemType.trim());
        if (StringUtils.hasText(status)) {
            String s = status.trim().toUpperCase();
            if (!"PENDING".equals(s) && !"RESOLVED".equals(s) && !"DISMISSED".equals(s)) {
                throw BusinessException.badRequest("状态只能是 PENDING、RESOLVED 或 DISMISSED");
            }
            feedback.setStatus(s);
        }
        if (note != null) feedback.setNote(note.trim());
        feedback.setUpdateTime(LocalDateTime.now());
        feedbackMapper.updateById(feedback);
        return toResponse(feedback, true);
    }

    private FeedbackResponse toResponse(ChatFeedback f) {
        return toResponse(f, false);
    }

    private FeedbackResponse toResponse(ChatFeedback f, boolean withContent) {
        String username = null, nickname = null, question = null, answer = null;
        if (withContent) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT u.username, u.nickname, t.question, m.content AS answer
                    FROM chat_feedback f LEFT JOIN sys_user u ON u.id=f.user_id
                    LEFT JOIN chat_message m ON m.id=f.message_id
                    LEFT JOIN chat_trace t ON t.trace_id=f.trace_id WHERE f.id=?
                    """, f.getId());
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                username = (String) row.get("username");
                nickname = (String) row.get("nickname");
                question = (String) row.get("question");
                answer = (String) row.get("answer");
            }
        }
        return new FeedbackResponse(f.getId(), f.getMessageId(), f.getConversationId(), f.getUserId(),
                username, nickname, f.getFeedbackType(), f.getReason(), f.getProblemType(),
                f.getStatus(), f.getNote(), f.getTraceId(), question, answer,
                f.getCreateTime() == null ? null : f.getCreateTime().format(TIME),
                f.getUpdateTime() == null ? null : f.getUpdateTime().format(TIME));
    }

    private FeedbackResponse mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        Timestamp updated = rs.getTimestamp("update_time");
        return new FeedbackResponse(rs.getLong("id"), rs.getLong("message_id"), rs.getLong("conversation_id"),
                rs.getLong("user_id"), rs.getString("username"), rs.getString("nickname"),
                rs.getString("feedback_type"), rs.getString("reason"), rs.getString("problem_type"),
                rs.getString("status"), rs.getString("note"), rs.getString("trace_id"),
                rs.getString("question"), rs.getString("answer"),
                created == null ? null : created.toLocalDateTime().format(TIME),
                updated == null ? null : updated.toLocalDateTime().format(TIME));
    }
}
