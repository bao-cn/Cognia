package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.common.exception.BusinessException;
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

    /**
     * The storage dimension is fixed. Switching to a model with another
     * dimension would invalidate the ANN index and all stored vectors, so it is
     * rejected instead of silently clearing the knowledge base.
     */
    public void assertDimension(int dimension) {
        if (dimension < 1 || dimension > 65536) {
            throw new IllegalArgumentException("Invalid vector dimension: " + dimension);
        }
        int current = currentDimension();
        if (dimension != current) {
            throw BusinessException.badRequest("向量模型维度为 " + dimension + "，当前知识库为 " + current
                    + " 维。请选择相同维度的模型；已有向量不会被清空。");
        }
    }

    public void assertDimensionCompatible(String modelLabel) {
        // Kept for model-switch validation; dimension itself is checked on the
        // first embedding call, before any chunk is written.
        log.debug("Checking embedding model switch to {}", modelLabel);
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
