package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.notification.NotificationResponse;

import java.util.List;
import java.util.Map;

/**
 * Persisted notification inbox plus online delivery through SSE.
 */
public interface NotificationService {

    void notify(Long userId, String type, String title, String content, Map<String, Object> payload);

    void notifyAdmins(String type, String title, String content, Map<String, Object> payload);

    PageResult<NotificationResponse> page(Long userId, long page, long size, boolean unreadOnly);

    long unreadCount(Long userId);

    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);

    List<NotificationResponse> undelivered(Long userId, int limit);

    void markDelivered(List<Long> ids);
}
