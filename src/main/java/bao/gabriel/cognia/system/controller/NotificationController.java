package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.notification.NotificationResponse;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.system.service.NotificationSseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Personal notification inbox and the SSE stream used by the header bell.
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService sseService;

    @GetMapping
    public ApiResponse<PageResult<NotificationResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = SecurityUtils.getUserId();
        return ApiResponse.success(notificationService.page(userId, page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.success(notificationService.unreadCount(SecurityUtils.getUserId()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(SecurityUtils.getUserId(), id);
        return ApiResponse.success();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(SecurityUtils.getUserId());
        return ApiResponse.success();
    }

    /**
     * Replays undelivered rows on connect, then keeps the connection open.
     * The frontend uses fetch() with the Authorization header (no token in the
     * query string).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = SecurityUtils.getUserId();
        SseEmitter emitter = sseService.connect(userId);
        List<NotificationResponse> pending = notificationService.undelivered(userId, 50);
        List<Long> delivered = new java.util.ArrayList<>();
        for (NotificationResponse notification : pending) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.id()))
                        .name("notification")
                        .data(notification));
                delivered.add(notification.id());
            } catch (Exception ex) {
                break;
            }
        }
        if (!delivered.isEmpty()) {
            notificationService.markDelivered(delivered);
        }
        sseService.sendUnreadCount(userId, notificationService.unreadCount(userId));
        return emitter;
    }
}
