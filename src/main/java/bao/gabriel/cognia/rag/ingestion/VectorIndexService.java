package bao.gabriel.cognia.rag.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIndexService {
    public static final String INDEX_NAME = "idx_document_chunk_embedding_hnsw";
    private final JdbcTemplate jdbcTemplate;
    private final IngestionSettingService settingService;
    private volatile String lastMessage = "尚未创建";

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread thread = new Thread(() -> {
            try { ensureIndex(); } catch (Exception ex) { log.warn("HNSW index ensure failed: {}", ex.getMessage()); }
        }, "hnsw-index-init");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized Map<String, Object> ensureIndex() {
        IngestionSetting setting = settingService.get();
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            Boolean exists = jdbcTemplate.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_indexes WHERE tablename = 'document_chunk' AND indexname = ?)
                    """, Boolean.class, INDEX_NAME);
            if (!Boolean.TRUE.equals(exists)) {
                jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + INDEX_NAME
                        + " ON document_chunk USING hnsw (embedding vector_cosine_ops) WITH (m = "
                        + setting.hnswM() + ", ef_construction = " + setting.hnswEfConstruction() + ")");
            }
            jdbcTemplate.execute("ANALYZE document_chunk");
            lastMessage = "HNSW 索引已就绪 (m=" + setting.hnswM() + ", ef_construction="
                    + setting.hnswEfConstruction() + ", ef_search=" + setting.hnswEfSearch() + ")";
            return status(true);
        } catch (Exception ex) {
            lastMessage = "创建失败：" + ex.getMessage();
            log.warn("Unable to create HNSW index: {}", ex.getMessage());
            return status(false);
        }
    }

    public synchronized Map<String, Object> rebuild() {
        try {
            jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX_NAME);
            return ensureIndex();
        } catch (Exception ex) {
            lastMessage = "重建失败：" + ex.getMessage();
            return status(false);
        }
    }

    public Map<String, Object> status(boolean ready) {
        return Map.of("ready", ready, "index", INDEX_NAME, "message", lastMessage);
    }

    public Map<String, Object> status() {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_indexes WHERE tablename = 'document_chunk' AND indexname = ?)
                """, Boolean.class, INDEX_NAME);
        return Map.of("ready", Boolean.TRUE.equals(exists), "index", INDEX_NAME, "message", lastMessage);
    }
}
