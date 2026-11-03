package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.discover.DiscoverChannelResponse;

import java.util.List;

public interface DiscoverService {

    PageResult<DiscoverChannelResponse> channels(String tab, String period, long page, long size, Long viewerId);

    /**
     * Public channels the viewer follows, most recently followed first.
     */
    List<DiscoverChannelResponse> subscriptions(Long viewerId);

    void subscribe(Long channelId, Long userId);

    void unsubscribe(Long channelId, Long userId);

    void subscribeNotify(Long channelId, Long userId);

    void unsubscribeNotify(Long channelId, Long userId);

    void setFeatured(Long channelId, boolean featured);
}
