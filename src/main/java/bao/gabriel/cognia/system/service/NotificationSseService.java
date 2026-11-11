package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.notification.NotificationResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory registry of open SSE connections (one user may have several tabs).
 */
public interface NotificationSseService {

    SseEmitter connect(Long userId);

    boolean send(Long userId, NotificationResponse notification);

    boolean sendUnreadCount(Long userId, long unreadCount);

    boolean isOnline(Long userId);

    long onlineCount();
}
