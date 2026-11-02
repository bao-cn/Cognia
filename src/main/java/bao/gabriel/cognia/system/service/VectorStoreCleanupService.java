package bao.gabriel.cognia.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Removes pgvector rows for a document. The knowledge-base vector table is
 * created lazily by Spring AI; cleanup must tolerate it not existing yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreCleanupService {

    private final JdbcTemplate jdbcTemplate;

    public int deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return 0;
        }
        int deleted = 0;
        try {
            deleted += jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
        } catch (Exception ex) {
            log.warn("Unable to delete document chunks for document {}: {}", documentId, ex.getMessage());
        }
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT to_regclass('public.vector_store') IS NOT NULL", Boolean.class);
            if (Boolean.TRUE.equals(exists)) {
                String id = String.valueOf(documentId);
                deleted += jdbcTemplate.update("""
                        DELETE FROM vector_store
                        WHERE metadata->>'documentId' = ?
                           OR metadata->>'document_id' = ?
                        """, id, id);
            }
        } catch (Exception ex) {
            log.warn("Unable to delete pgvector rows for document {}: {}", documentId, ex.getMessage());
        }
        return deleted;
    }
}
