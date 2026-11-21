package bao.gabriel.cognia.rag.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import bao.gabriel.cognia.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionQueueService {

    private final JdbcTemplate jdbcTemplate;
    private final IngestionSettingService settingService;
    private final IngestionJobMapper jobMapper;

    public boolean enqueue(Long documentId, Long channelId, String step) {
        Integer active = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sys_ingestion_job
                WHERE document_id = ? AND step = ? AND status IN ('QUEUED','RUNNING')
                """, Integer.class, documentId, step);
        if (active != null && active > 0) {
            return false;
        }
        Integer depth = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sys_ingestion_job WHERE status IN ('QUEUED','RUNNING')
                """, Integer.class);
        if (depth != null && depth >= settingService.get().queueLimit()) {
            return false;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_ingestion_job(document_id, channel_id, step, status, attempts, max_attempts, available_at, created_at)
                VALUES (?, ?, ?, 'QUEUED', 0, ?, NOW(), NOW())
                """, documentId, channelId, step, settingService.get().maxRetries());
        return true;
    }

    public void ensureCapacity() {
        if (depth() >= settingService.get().queueLimit()) {
            throw BusinessException.conflict("文档处理队列已满，请稍后再试");
        }
    }

    public IngestionJob claim(String workerId) {
        List<IngestionJob> rows = jdbcTemplate.query("""
                WITH candidate AS (
                    SELECT id FROM sys_ingestion_job
                    WHERE (status = 'QUEUED' AND available_at <= NOW())
                       OR (status = 'RUNNING' AND lease_until < NOW())
                    ORDER BY id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE sys_ingestion_job j
                SET status = 'RUNNING', lease_owner = ?, lease_until = NOW() + INTERVAL '2 minutes',
                    attempts = j.attempts + 1, started_at = COALESCE(j.started_at, NOW()), error_message = NULL
                FROM candidate
                WHERE j.id = candidate.id
                RETURNING j.*
                """, (rs, rowNum) -> map(rs), workerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void heartbeatOwner(String workerId) {
        jdbcTemplate.update("""
                UPDATE sys_ingestion_job SET lease_until = NOW() + INTERVAL '2 minutes'
                WHERE lease_owner = ? AND status = 'RUNNING'
                """, workerId);
    }

    public void heartbeat(Long jobId, String workerId) {
        jdbcTemplate.update("""
                UPDATE sys_ingestion_job SET lease_until = NOW() + INTERVAL '2 minutes'
                WHERE id = ? AND lease_owner = ? AND status = 'RUNNING'
                """, jobId, workerId);
    }

    public void complete(Long jobId) {
        jdbcTemplate.update("""
                UPDATE sys_ingestion_job SET status = 'SUCCEEDED', lease_owner = NULL, lease_until = NULL,
                    finished_at = NOW()
                WHERE id = ? AND status = 'RUNNING'
                """, jobId);
    }

    public boolean isCancelled(Long jobId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_ingestion_job WHERE id = ? AND status = 'CANCELLED'",
                Integer.class, jobId);
        return count != null && count > 0;
    }

    public int cancelDocument(Long documentId) {
        return jdbcTemplate.update("""
                UPDATE sys_ingestion_job SET status = 'CANCELLED', finished_at = NOW(), lease_owner = NULL,
                       lease_until = NULL
                WHERE document_id = ? AND status IN ('QUEUED','RUNNING')
                """, documentId);
    }

    public int cancelChannel(Long channelId) {
        return jdbcTemplate.update("""
                UPDATE sys_ingestion_job
                SET status = 'CANCELLED', lease_owner = NULL, lease_until = NULL,
                    finished_at = NOW(), error_message = COALESCE(error_message, '用户取消')
                WHERE channel_id = ? AND status IN ('QUEUED','RUNNING')
                """, channelId);
    }

    /** Returns QUEUED for a retry, or DEAD when attempts are exhausted. */
    public String fail(IngestionJob job, String message) {
        IngestionSetting setting = settingService.get();
        boolean dead = job.getAttempts() >= job.getMaxAttempts();
        if (dead) {
            jdbcTemplate.update("""
                    UPDATE sys_ingestion_job SET status = 'DEAD', error_message = ?, lease_owner = NULL,
                        lease_until = NULL, finished_at = NOW()
                    WHERE id = ? AND status = 'RUNNING'
                    """, message, job.getId());
            return "DEAD";
        }
        long delay = (long) setting.retryBackoffSeconds() * (1L << Math.min(job.getAttempts(), 6));
        jdbcTemplate.update("""
                UPDATE sys_ingestion_job SET status = 'QUEUED', error_message = ?, lease_owner = NULL,
                    lease_until = NULL, available_at = NOW() + (? * INTERVAL '1 second')
                WHERE id = ? AND status = 'RUNNING'
                """, message, delay, job.getId());
        return "QUEUED";
    }

    public void requeueDead(Long id) {
        jdbcTemplate.update("""
                UPDATE sys_ingestion_job SET status = 'QUEUED', attempts = 0, available_at = NOW(),
                    lease_owner = NULL, lease_until = NULL, error_message = NULL, finished_at = NULL
                WHERE id = ? AND status = 'DEAD'
                """, id);
    }

    public int depth() {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sys_ingestion_job WHERE status IN ('QUEUED','RUNNING')
                """, Integer.class);
        return value == null ? 0 : value;
    }

    public List<IngestionJob> recent(int limit) {
        return jobMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IngestionJob>()
                .orderByDesc(IngestionJob::getId).last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public List<IngestionJob> dead(int limit) {
        return jobMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IngestionJob>()
                .eq(IngestionJob::getStatus, "DEAD").orderByDesc(IngestionJob::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    private IngestionJob map(ResultSet rs) throws SQLException {
        IngestionJob job = new IngestionJob();
        job.setId(rs.getLong("id"));
        job.setDocumentId(rs.getLong("document_id"));
        job.setChannelId(rs.getObject("channel_id") == null ? null : rs.getLong("channel_id"));
        job.setStep(rs.getString("step"));
        job.setStatus(rs.getString("status"));
        job.setAttempts(rs.getInt("attempts"));
        job.setMaxAttempts(rs.getInt("max_attempts"));
        job.setErrorMessage(rs.getString("error_message"));
        return job;
    }
}
