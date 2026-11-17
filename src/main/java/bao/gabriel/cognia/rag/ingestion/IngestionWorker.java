package bao.gabriel.cognia.rag.ingestion;

import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.service.ChannelAlertService;
import bao.gabriel.cognia.system.service.DocumentProcessingNotifier;
import bao.gabriel.cognia.system.service.DocumentIngestionService;
import bao.gabriel.cognia.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionWorker {
    private final IngestionQueueService queueService;
    private final IngestionSettingService settingService;
    private final DocumentIngestionService ingestionService;
    private final KbDocumentMapper documentMapper;
    private final NotificationService notificationService;
    private final ChannelAlertService channelAlertService;
    private final DocumentProcessingNotifier processingNotifier;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "ingestion-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger running = new AtomicInteger();
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void tick() {
        int concurrency = settingService.get().concurrency();
        while (running.get() < concurrency) {
            IngestionJob job = queueService.claim(workerId);
            if (job == null) return;
            running.incrementAndGet();
            executor.submit(() -> {
                try { execute(job); }
                finally { running.decrementAndGet(); }
            });
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void renewLeases() {
        queueService.heartbeatOwner(workerId);
    }

    private void execute(IngestionJob job) {
        try {
            if ("PARSE".equals(job.getStep())) {
                ingestionService.parse(job.getDocumentId());
            } else if ("EMBED".equals(job.getStep())) {
                ingestionService.embed(job.getDocumentId());
            } else {
                throw new IllegalStateException("未知处理步骤: " + job.getStep());
            }
            queueService.complete(job.getId());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("Ingestion job {} ({}, document {}) failed: {}", job.getId(), job.getStep(),
                    job.getDocumentId(), message);
            String status = queueService.fail(job, message);
            if ("DEAD".equals(status)) {
                markFailed(job, message);
            }
        }
    }

    private void markFailed(IngestionJob job, String message) {
        KbDocument document = documentMapper.selectById(job.getDocumentId());
        if (document == null) return;
        document.setStatus("FAILED");
        document.setErrorMessage(message.length() > 900 ? message.substring(0, 900) : message);
        documentMapper.updateById(document);
        processingNotifier.finished(document, false);
    }
}
