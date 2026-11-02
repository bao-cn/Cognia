package bao.gabriel.cognia.system.dto.notification;

import java.util.Map;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String content,
        Map<String, Object> payload,
        boolean read,
        String createTime
) {
}
