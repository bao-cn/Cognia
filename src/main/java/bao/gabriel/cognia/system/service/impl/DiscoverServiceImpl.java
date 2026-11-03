package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.discover.DiscoverChannelResponse;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.ChannelNotifySubscription;
import bao.gabriel.cognia.system.entity.ChannelSubscription;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.ChannelNotifySubscriptionMapper;
import bao.gabriel.cognia.system.mapper.ChannelSubscriptionMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.DiscoverService;
import bao.gabriel.cognia.system.service.DocumentStateMachine;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoverServiceImpl implements DiscoverService {

    private static final int MAX_PAGE_SIZE = 30;

    private final ChannelMapper channelMapper;
    private final ChannelSubscriptionMapper subscriptionMapper;
    private final ChannelNotifySubscriptionMapper notifySubscriptionMapper;
    private final KbDocumentMapper documentMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final SiteSettingService siteSettingService;

    @Override
    public PageResult<DiscoverChannelResponse> channels(String tab, String period, long page, long size,
                                                        Long viewerId) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String normalizedTab = StringUtils.hasText(tab) ? tab.trim().toLowerCase() : "featured";

        List<Channel> candidates;
        if ("featured".equals(normalizedTab)) {
            candidates = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                    .eq(Channel::getIsPublic, true)
                    .eq(Channel::getFeatured, true)
                    .orderByDesc(Channel::getFeaturedTime)
                    .orderByDesc(Channel::getId));
        } else {
            LocalDateTime since = LocalDate.now().minusDays(periodDays(period)).atStartOfDay();
            Map<Long, Long> counts = countSubscriptions(subscriptionMapper.selectList(
                    new LambdaQueryWrapper<ChannelSubscription>()
                            .ge(ChannelSubscription::getCreateTime, since)));
            candidates = new ArrayList<>(channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                    .eq(Channel::getIsPublic, true)));
            candidates.sort(Comparator
                    .comparingLong((Channel channel) -> counts.getOrDefault(channel.getId(), 0L))
                    .reversed()
                    .thenComparing(Comparator.comparingLong(Channel::getId).reversed()));
        }

        long total = candidates.size();
        int from = (int) Math.min((safePage - 1) * safeSize, total);
        int to = (int) Math.min(from + safeSize, total);
        List<Channel> pageChannels = candidates.subList(from, to);
        if (pageChannels.isEmpty()) {
            return PageResult.empty(safePage, safeSize);
        }

        List<Long> channelIds = pageChannels.stream().map(Channel::getId).toList();
        Map<Long, Long> totalSubscriptions = countSubscriptions(subscriptionMapper.selectList(
                new LambdaQueryWrapper<ChannelSubscription>().in(ChannelSubscription::getChannelId, channelIds)));
        Set<Long> subscribed = subscribedIds(viewerId, channelIds);
        Set<Long> notifySubscribed = notifySubscribedIds(viewerId, channelIds);
        List<DiscoverChannelResponse> records = pageChannels.stream()
                .map(channel -> toResponse(channel, totalSubscriptions.getOrDefault(channel.getId(), 0L),
                        subscribed.contains(channel.getId()), notifySubscribed.contains(channel.getId())))
                .toList();
        return PageResult.of(records, total, safePage, safeSize);
    }

    @Override
    public List<DiscoverChannelResponse> subscriptions(Long viewerId) {
        if (viewerId == null) {
            return List.of();
        }
        List<ChannelSubscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<ChannelSubscription>()
                        .eq(ChannelSubscription::getUserId, viewerId)
                        .orderByDesc(ChannelSubscription::getCreateTime)
                        .orderByDesc(ChannelSubscription::getId));
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> orderedIds = subscriptions.stream().map(ChannelSubscription::getChannelId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                        .in(Channel::getId, orderedIds)
                        .eq(Channel::getIsPublic, true))
                .stream().collect(Collectors.toMap(Channel::getId, channel -> channel));
        List<Long> channelIds = orderedIds.stream().filter(channels::containsKey).toList();
        Map<Long, Long> subscriptionCounts = countSubscriptions(subscriptionMapper.selectList(
                new LambdaQueryWrapper<ChannelSubscription>().in(ChannelSubscription::getChannelId, channelIds)));
        Set<Long> notifySubscribed = notifySubscribedIds(viewerId, channelIds);
        List<DiscoverChannelResponse> records = new ArrayList<>();
        for (Long channelId : channelIds) {
            records.add(toResponse(channels.get(channelId),
                    subscriptionCounts.getOrDefault(channelId, 0L), true,
                    notifySubscribed.contains(channelId)));
        }
        return records;
    }

    @Override
    @Transactional
    public void subscribe(Long channelId, Long userId) {
        requirePublicChannel(channelId);
        boolean exists = subscriptionMapper.selectCount(new LambdaQueryWrapper<ChannelSubscription>()
                .eq(ChannelSubscription::getChannelId, channelId)
                .eq(ChannelSubscription::getUserId, userId)) > 0;
        if (exists) {
            throw BusinessException.conflict("你已订阅该频道");
        }
        ChannelSubscription subscription = new ChannelSubscription();
        subscription.setChannelId(channelId);
        subscription.setUserId(userId);
        subscription.setCreateTime(LocalDateTime.now());
        subscriptionMapper.insert(subscription);
    }

    @Override
    @Transactional
    public void unsubscribe(Long channelId, Long userId) {
        subscriptionMapper.delete(new LambdaQueryWrapper<ChannelSubscription>()
                .eq(ChannelSubscription::getChannelId, channelId)
                .eq(ChannelSubscription::getUserId, userId));
    }

    @Override
    @Transactional
    public void subscribeNotify(Long channelId, Long userId) {
        requirePublicChannel(channelId);
        boolean exists = notifySubscriptionMapper.selectCount(new LambdaQueryWrapper<ChannelNotifySubscription>()
                .eq(ChannelNotifySubscription::getChannelId, channelId)
                .eq(ChannelNotifySubscription::getUserId, userId)) > 0;
        if (exists) {
            throw BusinessException.conflict("你已开启该频道的通知");
        }
        ChannelNotifySubscription subscription = new ChannelNotifySubscription();
        subscription.setChannelId(channelId);
        subscription.setUserId(userId);
        subscription.setCreateTime(LocalDateTime.now());
        notifySubscriptionMapper.insert(subscription);
    }

    @Override
    @Transactional
    public void unsubscribeNotify(Long channelId, Long userId) {
        notifySubscriptionMapper.delete(new LambdaQueryWrapper<ChannelNotifySubscription>()
                .eq(ChannelNotifySubscription::getChannelId, channelId)
                .eq(ChannelNotifySubscription::getUserId, userId));
    }

    @Override
    @Transactional
    public void setFeatured(Long channelId, boolean featured) {
        Channel channel = channelMapper.selectById(channelId);
        if (channel == null) {
            throw BusinessException.notFound("频道不存在");
        }
        channel.setFeatured(featured);
        channel.setFeaturedTime(featured ? LocalDateTime.now() : null);
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
    }

    private Channel requirePublicChannel(Long channelId) {
        Channel channel = channelId == null ? null : channelMapper.selectById(channelId);
        if (channel == null) {
            throw BusinessException.notFound("频道不存在");
        }
        if (!channel.isPublic()) {
            throw BusinessException.badRequest("该频道未公开");
        }
        return channel;
    }

    private DiscoverChannelResponse toResponse(Channel channel, long subscriberCount, boolean subscribed,
                                               boolean notifySubscribed) {
        String ownerName;
        String ownerSlug;
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            User owner = userMapper.selectById(channel.getOwnerId());
            ownerName = owner == null ? "未知用户"
                    : (StringUtils.hasText(owner.getNickname()) ? owner.getNickname() : owner.getUsername());
            ownerSlug = owner == null ? null : owner.getUsername();
        } else {
            Team owner = teamMapper.selectById(channel.getOwnerId());
            ownerName = owner == null ? "未知团队" : owner.getName();
            ownerSlug = owner == null ? null : owner.getSlug();
        }
        long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channel.getId())
                .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
        return new DiscoverChannelResponse(
                String.valueOf(channel.getId()), channel.getOwnerType(),
                String.valueOf(channel.getOwnerId()), ownerName, ownerSlug,
                channel.getName(), channel.getSlug(), channel.getDescription(),
                channel.isPublic(), channel.featured(), channel.webSearchEnabled(),
                subscriberCount, subscribed, notifySubscribed, documentCount,
                siteSettingService.formatDateTime(channel.getUpdateTime()));
    }

    private Set<Long> subscribedIds(Long viewerId, List<Long> channelIds) {
        if (viewerId == null || channelIds.isEmpty()) {
            return Set.of();
        }
        return subscriptionMapper.selectList(new LambdaQueryWrapper<ChannelSubscription>()
                        .eq(ChannelSubscription::getUserId, viewerId)
                        .in(ChannelSubscription::getChannelId, channelIds))
                .stream().map(ChannelSubscription::getChannelId).collect(Collectors.toSet());
    }

    private Set<Long> notifySubscribedIds(Long viewerId, List<Long> channelIds) {
        if (viewerId == null || channelIds.isEmpty()) {
            return Set.of();
        }
        return notifySubscriptionMapper.selectList(new LambdaQueryWrapper<ChannelNotifySubscription>()
                        .eq(ChannelNotifySubscription::getUserId, viewerId)
                        .in(ChannelNotifySubscription::getChannelId, channelIds))
                .stream().map(ChannelNotifySubscription::getChannelId).collect(Collectors.toSet());
    }

    private Map<Long, Long> countSubscriptions(List<ChannelSubscription> subscriptions) {
        return subscriptions.stream().collect(Collectors.groupingBy(ChannelSubscription::getChannelId,
                Collectors.counting()));
    }

    private int periodDays(String period) {
        if (period == null) {
            return 7;
        }
        return switch (period.trim().toLowerCase()) {
            case "month" -> 30;
            case "year" -> 365;
            default -> 7;
        };
    }
}
