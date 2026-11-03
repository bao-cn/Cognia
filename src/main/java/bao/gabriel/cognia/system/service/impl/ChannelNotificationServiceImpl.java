package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.channel.ChannelNotificationRequest;
import bao.gabriel.cognia.system.dto.channel.ChannelNotificationResponse;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.entity.ChannelNotification;
import bao.gabriel.cognia.system.entity.ChannelNotifySubscription;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.ChannelNotificationMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.mapper.ChannelNotifySubscriptionMapper;
import bao.gabriel.cognia.system.service.ChannelNotificationService;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.NotificationService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelNotificationServiceImpl implements ChannelNotificationService {

    private static final String TYPE_CHANNEL_NOTIFICATION = "CHANNEL_NOTIFICATION";
    private static final String DUPLICATE_MESSAGE = "该频道今天已发送过通知";

    private final ChannelMapper channelMapper;
    private final ChannelNotificationMapper notificationMapper;
    private final ChannelNotifySubscriptionMapper notifySubscriptionMapper;
    private final ChannelService channelService;
    private final NotificationService notificationService;
    private final SiteSettingService siteSettingService;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;

    @Override
    @Transactional
    public ChannelNotificationResponse send(Long channelId, Long senderId, ChannelNotificationRequest request) {
        Channel channel = channelId == null ? null : channelMapper.selectById(channelId);
        if (channel == null) {
            throw BusinessException.notFound("频道不存在");
        }
        if (!channelService.hasNotificationAccess(channelId, senderId)) {
            throw BusinessException.forbidden("你没有发送频道通知的权限");
        }
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayCount = notificationMapper.selectCount(new LambdaQueryWrapper<ChannelNotification>()
                .eq(ChannelNotification::getChannelId, channelId)
                .ge(ChannelNotification::getCreateTime, startOfDay));
        if (todayCount > 0) {
            throw BusinessException.conflict(DUPLICATE_MESSAGE);
        }
        ChannelNotification notification = new ChannelNotification();
        notification.setChannelId(channelId);
        notification.setSenderId(senderId);
        notification.setTitle(request.title().trim());
        notification.setContent(request.content().trim());
        notification.setCreateTime(LocalDateTime.now());
        notification.setNotifiedDate(LocalDate.now());
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict(DUPLICATE_MESSAGE);
        }

        List<Long> receivers = notifySubscriptionMapper.selectList(
                        new LambdaQueryWrapper<ChannelNotifySubscription>()
                                .eq(ChannelNotifySubscription::getChannelId, channelId))
                .stream().map(ChannelNotifySubscription::getUserId).distinct().toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelId", String.valueOf(channelId));
        payload.put("channelName", channel.getName());
        payload.put("channelSlug", channel.getSlug());
        payload.put("ownerType", channel.getOwnerType());
        payload.put("ownerId", String.valueOf(channel.getOwnerId()));
        String ownerSlug = null;
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            User owner = userMapper.selectById(channel.getOwnerId());
            ownerSlug = owner == null ? null : owner.getUsername();
        } else {
            Team owner = teamMapper.selectById(channel.getOwnerId());
            ownerSlug = owner == null ? null : owner.getSlug();
        }
        if (ownerSlug != null) {
            payload.put("ownerSlug", ownerSlug);
        }
        String inboxContent = truncate(notification.getContent(), 1000);
        for (Long receiver : receivers) {
            try {
                notificationService.notify(receiver, TYPE_CHANNEL_NOTIFICATION,
                        notification.getTitle(), inboxContent, payload);
            } catch (RuntimeException ex) {
                log.warn("Unable to deliver channel notification {} to user {}: {}",
                        notification.getId(), receiver, ex.getMessage());
            }
        }
        return new ChannelNotificationResponse(notification.getId(), channelId, notification.getTitle(),
                notification.getContent(), siteSettingService.formatDateTime(notification.getCreateTime()));
    }

    /** The generic inbox column is shorter than a channel notification body. */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
