package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.entity.KbDocument;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Document lifecycle:
 *
 * <pre>
 * PENDING ──▶ PROCESSING ──▶ READY
 *    │            │
 *    └────────────┴──▶ FAILED ──▶ PENDING (manual retry)
 *    └────────────┴──▶ DELETED
 * READY ──▶ DELETED
 * </pre>
 *
 * Any state may be moved to DELETED; a DELETED document is terminal.
 */
@Service
public class DocumentStateMachine {

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED = Map.of(
            DocumentStatus.PENDING, Set.of(DocumentStatus.PROCESSING, DocumentStatus.FAILED, DocumentStatus.DELETED),
            DocumentStatus.PROCESSING, Set.of(DocumentStatus.READY, DocumentStatus.FAILED, DocumentStatus.DELETED),
            DocumentStatus.READY, Set.of(DocumentStatus.DELETED),
            DocumentStatus.FAILED, Set.of(DocumentStatus.PENDING, DocumentStatus.DELETED),
            DocumentStatus.DELETED, Set.of()
    );

    public KbDocument transition(KbDocument document, DocumentStatus target, String errorMessage) {
        DocumentStatus current = DocumentStatus.parse(document.getStatus());
        if (!current.canTransitionTo(target)) {
            throw BusinessException.badRequest(
                    "文档状态不能从 " + current + " 变更为 " + target);
        }
        document.setStatus(target.name());
        document.setErrorMessage(target == DocumentStatus.FAILED ? errorMessage : null);
        document.setUpdateTime(LocalDateTime.now());
        if (target == DocumentStatus.READY || target == DocumentStatus.FAILED
                || target == DocumentStatus.DELETED) {
            document.setProcessedAt(LocalDateTime.now());
        }
        if (target == DocumentStatus.PROCESSING) {
            document.setProcessedAt(null);
        }
        return document;
    }

    /**
     * Moves a failed document back to the queue. The caller persists it and
     * re-enqueues the actual processing job.
     */
    public KbDocument retry(KbDocument document) {
        transition(document, DocumentStatus.PENDING, null);
        document.setErrorMessage(null);
        document.setRetryCount(document.getRetryCount() == null ? 1 : document.getRetryCount() + 1);
        return document;
    }

    public enum DocumentStatus {
        PENDING,
        PROCESSING,
        READY,
        FAILED,
        DELETED;

        public boolean canTransitionTo(DocumentStatus target) {
            return ALLOWED.getOrDefault(this, Set.of()).contains(target);
        }

        public boolean isTerminal() {
            return this == DELETED;
        }

        public static DocumentStatus parse(String value) {
            if (value == null || value.isBlank()) {
                return PENDING;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException ex) {
                return PENDING;
            }
        }
    }
}
