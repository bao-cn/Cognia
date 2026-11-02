package bao.gabriel.cognia.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDimensionService {

    private final JdbcTemplate jdbcTemplate;
    private volatile Integer currentDimension;

    public int currentDimension() {
        Integer cached = currentDimension;
        if (cached != null) {
            return cached;
        }
        try {
            String type = jdbcTemplate.queryForObject(
                    "SELECT format_type(atttypid, atttypmod) FROM pg_attribute "
                            + "WHERE attrelid = 'document_chunk'::regclass AND attname = 'embedding'",
                    String.class);
            int dimension = parse(type);
            currentDimension = dimension;
            return dimension;
        } catch (Exception ex) {
            log.warn("Unable to read document_chunk.embedding dimension: {}", ex.getMessage());
            return 1536;
        }
    }

    public synchronized void ensureDimension(int dimension) {
        if (dimension < 1 || dimension > 65536) {
            throw new IllegalArgumentException("Invalid vector dimension: " + dimension);
        }
        if (dimension == currentDimension()) {
            return;
        }
        log.info("Changing document_chunk.embedding dimension from {} to {}", currentDimension(), dimension);
        jdbcTemplate.execute("UPDATE document_chunk SET embedding = NULL");
        jdbcTemplate.execute("ALTER TABLE document_chunk ALTER COLUMN embedding TYPE vector(" + dimension + ")");
        currentDimension = dimension;
    }

    private int parse(String type) {
        if (type == null) {
            return 1536;
        }
        int start = type.indexOf('(');
        int end = type.indexOf(')');
        if (start < 0 || end <= start) {
            return 1536;
        }
        try {
            return Integer.parseInt(type.substring(start + 1, end).trim());
        } catch (NumberFormatException ex) {
            return 1536;
        }
    }
}
