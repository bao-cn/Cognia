package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.notification.NotificationResponse;
import bao.gabriel.cognia.system.entity.Notification;
import bao.gabriel.cognia.system.entity.Role;
import bao.gabriel.cognia.system.mapper.NotificationMapper;
import bao.gabriel.cognia.system.mapper.RoleMapper;
import bao.gabriel.cognia.system.mapper.UserRoleMapper;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.system.service.NotificationSseService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationMapper notificationMapper;
    private final NotificationSseService sseService;
    private final SiteSettingService siteSettingService;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public void notify(Long userId, String type, String title, String content, Map<String, Object> payload) {
        if (userId == null) {
            return;
        }
        Notification entity = new Notification();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setPayloadJson(writePayload(payload));
        entity.setReadFlag(false);
        entity.setDelivered(false);
        entity.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(entity);

        NotificationResponse response = toResponse(entity);
        if (sseService.send(userId, response)) {
            markDelivered(List.of(entity.getId()));
        }
        sseService.sendUnreadCount(userId, unreadCount(userId));
    }

    @Override
    public void notifyAdmins(String type, String title, String content, Map<String, Object> payload) {
        Role admin = roleMapper.selectByCode("ADMIN");
        if (admin == null) {
            return;
        }
        for (Long userId : userRoleMapper.selectUserIdsByRoleId(admin.getId())) {
            notify(userId, type, title, content, payload);
        }
    }

    @Override
    public PageResult<NotificationResponse> page(Long userId, long page, long size, boolean unreadOnly) {
        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(unreadOnly, Notification::getReadFlag, false)
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId);
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(), page, size);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, false));
    }

    @Override
    public void markRead(Long userId, Long notificationId) {
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .set(Notification::getReadFlag, true)
                .set(Notification::getReadTime, LocalDateTime.now()));
        if (updated == 0) {
            throw BusinessException.notFound("通知不存在");
        }
        sseService.sendUnreadCount(userId, unreadCount(userId));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, false)
                .set(Notification::getReadFlag, true)
                .set(Notification::getReadTime, LocalDateTime.now()));
        sseService.sendUnreadCount(userId, 0);
    }

    @Override
    public List<NotificationResponse> undelivered(Long userId, int limit) {
        List<Notification> rows = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getDelivered, false)
                .orderByAsc(Notification::getCreateTime)
                .orderByAsc(Notification::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        return rows.stream().map(this::toResponse).toList();
    }

    @Override
    public void markDelivered(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .in(Notification::getId, ids)
                .set(Notification::getDelivered, true)
                .set(Notification::getDeliveredTime, LocalDateTime.now()));
    }

    private NotificationResponse toResponse(Notification entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getType(),
                entity.getTitle(),
                entity.getContent(),
                readPayload(entity.getPayloadJson()),
                entity.isRead(),
                entity.getCreateTime() == null ? null : siteSettingService.formatDateTime(entity.getCreateTime()));
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("Unable to serialize notification payload: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
