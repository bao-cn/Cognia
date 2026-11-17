package bao.gabriel.cognia.rag.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IngestionMetricsService {
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingHealthService healthService;
    private final VectorIndexService indexService;
    private final IngestionQueueService queueService;

    public Map<String, Object> metrics() {
        Integer queueDepth = count("SELECT count(*) FROM sys_ingestion_job WHERE status = 'QUEUED'");
        Integer running = count("SELECT count(*) FROM sys_ingestion_job WHERE status = 'RUNNING'");
        Integer dead = count("SELECT count(*) FROM sys_ingestion_job WHERE status = 'DEAD'");
        Integer success = count("SELECT count(*) FROM sys_ingestion_job WHERE status = 'SUCCEEDED' AND finished_at > NOW() - INTERVAL '24 hours'");
        Integer failed = count("SELECT count(*) FROM sys_ingestion_job WHERE status = 'DEAD' AND finished_at > NOW() - INTERVAL '24 hours'");
        double failureRate = success + failed == 0 ? 0 : failed * 100.0 / (success + failed);
        Double avg = jdbcTemplate.queryForObject("""
                SELECT COALESCE(avg(EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000), 0)
                FROM sys_ingestion_job WHERE finished_at IS NOT NULL AND finished_at > NOW() - INTERVAL '24 hours'
                """, Double.class);
        Double p95 = jdbcTemplate.queryForObject("""
                SELECT COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (finished_at - started_at)) * 1000), 0)
                FROM sys_ingestion_job WHERE finished_at IS NOT NULL AND finished_at > NOW() - INTERVAL '24 hours'
                """, Double.class);
        List<Map<String, Object>> byChannel = jdbcTemplate.queryForList("""
                SELECT COALESCE(channel_id, 0) AS channel_id,
                       count(*) FILTER (WHERE status IN ('QUEUED','RUNNING')) AS queued,
                       count(*) FILTER (WHERE status = 'DEAD') AS dead,
                       count(*) FILTER (WHERE status = 'SUCCEEDED' AND finished_at > NOW() - INTERVAL '24 hours') AS succeeded,
                       count(*) FILTER (WHERE status = 'DEAD' AND finished_at > NOW() - INTERVAL '24 hours') AS failed
                FROM sys_ingestion_job GROUP BY channel_id ORDER BY failed DESC, dead DESC LIMIT 50
                """);
        List<Map<String, Object>> byModel = jdbcTemplate.queryForList("""
                SELECT COALESCE(model_label, 'unknown') AS model,
                       count(*) AS calls,
                       count(*) FILTER (WHERE success) AS success,
                       count(*) FILTER (WHERE NOT success) AS failed,
                       COALESCE(avg(latency_ms), 0) AS avg_latency_ms
                FROM sys_embedding_health WHERE create_time > NOW() - INTERVAL '24 hours'
                GROUP BY model_label ORDER BY calls DESC
                """);
        double healthRate = healthService.successRate();
        boolean healthy = healthRate >= 90;
        List<Map<String, Object>> failures = jdbcTemplate.queryForList("""
                SELECT id, document_id, channel_id, step, status, attempts, error_message, created_at
                FROM sys_ingestion_job WHERE status = 'DEAD' ORDER BY id DESC LIMIT 20
                """);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("queueDepth", queueDepth);
        result.put("running", running);
        result.put("dead", dead);
        result.put("succeeded24h", success);
        result.put("failed24h", failed);
        result.put("failureRate", failureRate);
        result.put("avgLatencyMs", avg == null ? 0 : avg);
        result.put("p95LatencyMs", p95 == null ? 0 : p95);
        result.put("embeddingSuccessRate", healthRate);
        result.put("embeddingHealthy", healthy);
        result.put("consecutiveFailures", healthService.consecutiveFailures());
        result.put("byChannel", byChannel);
        result.put("byModel", byModel);
        result.put("index", indexService.status());
        result.put("recentFailures", failures);
        return result;
    }

    private Integer count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
