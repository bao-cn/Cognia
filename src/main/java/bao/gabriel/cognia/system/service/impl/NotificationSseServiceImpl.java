package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.system.dto.notification.NotificationResponse;
import bao.gabriel.cognia.system.service.NotificationSseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps one long-lived SSE emitter per browser tab. Sends are best effort: a
 * dead emitter is removed and the notification stays undelivered in the DB.
 */
@Slf4j
@Service
public class NotificationSseServiceImpl implements NotificationSseService {

    private static final long SSE_TIMEOUT = 0L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("userId", userId)));
        } catch (IOException ex) {
            remove(userId, emitter);
        }
        return emitter;
    }

    @Override
    public boolean send(Long userId, NotificationResponse notification) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return false;
        }
        boolean sent = false;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.id() == null ? null : String.valueOf(notification.id()))
                        .name("notification")
                        .data(notification));
                sent = true;
            } catch (Exception ex) {
                remove(userId, emitter);
            }
        }
        return sent;
    }

    @Override
    public boolean sendUnreadCount(Long userId, long unreadCount) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return false;
        }
        boolean sent = false;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("unread-count").data(Map.of("count", unreadCount)));
                sent = true;
            } catch (Exception ex) {
                remove(userId, emitter);
            }
        }
        return sent;
    }

    @Override
    public boolean isOnline(Long userId) {
        List<SseEmitter> list = emitters.get(userId);
        return list != null && !list.isEmpty();
    }

    /**
     * Comment-only heartbeat keeps proxies and load balancers from closing an
     * otherwise idle stream.
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId, list);
            }
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // Already completed.
        }
    }

    @Override
    public long onlineCount() {
        return emitters.values().stream().filter(list -> !list.isEmpty()).count();
    }
}
