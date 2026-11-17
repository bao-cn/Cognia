package bao.gabriel.cognia.rag.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingHealthService {
    private final EmbeddingHealthMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public void record(String modelLabel, boolean success, long latencyMs, String error) {
        EmbeddingHealth health = new EmbeddingHealth();
        health.setModelLabel(modelLabel);
        health.setSuccess(success);
        health.setLatencyMs(latencyMs);
        health.setErrorMessage(error == null ? null : truncate(error, 900));
        health.setCreateTime(LocalDateTime.now());
        try { mapper.insert(health); } catch (Exception ex) { log.debug("embedding health insert failed: {}", ex.getMessage()); }
    }

    /** Percentage of successful embedding calls in the last 10 minutes. */
    public double successRate() {
        Double value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(100.0 * sum(CASE WHEN success THEN 1 ELSE 0 END) / NULLIF(count(*), 0), 100)
                FROM sys_embedding_health WHERE create_time > NOW() - INTERVAL '10 minutes'
                """, Double.class);
        return value == null ? 100.0 : value;
    }

    public int consecutiveFailures() {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM (
                    SELECT success FROM sys_embedding_health ORDER BY id DESC LIMIT 20
                ) t WHERE NOT success
                """, Integer.class);
        return value == null ? 0 : value;
    }

    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
