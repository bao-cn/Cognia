package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.system.dto.search.SearchResponse;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.ChatConversation;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.ChatConversationMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.GlobalSearchService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    private static final int LIMIT = 8;

    private final ChannelMapper channelMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final ChatConversationMapper conversationMapper;
    private final SiteSettingService siteSettingService;

    @Override
    public SearchResponse search(String keyword, Long userId) {
        if (!StringUtils.hasText(keyword) || userId == null) {
            return SearchResponse.empty();
        }
        String q = keyword.trim();
        return new SearchResponse(searchChannels(q), searchUsers(q), searchConversations(q, userId));
    }

    private List<SearchResponse.ChannelHit> searchChannels(String q) {
        List<Channel> channels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getIsPublic, true)
                .and(w -> w.like(Channel::getName, q).or().like(Channel::getSlug, q))
                .orderByDesc(Channel::getPinned)
                .orderByDesc(Channel::getUpdateTime)
                .last("LIMIT " + LIMIT));
        List<SearchResponse.ChannelHit> hits = new ArrayList<>();
        for (Channel channel : channels) {
            String ownerSlug = resolveOwnerSlug(channel);
            if (ownerSlug == null) {
                continue;
            }
            hits.add(new SearchResponse.ChannelHit(channel.getId(), channel.getName(), channel.getSlug(),
                    channel.getDescription(), channel.getOwnerType(), ownerSlug));
        }
        return hits;
    }

    private String resolveOwnerSlug(Channel channel) {
        if ("USER".equals(channel.getOwnerType())) {
            User owner = userMapper.selectById(channel.getOwnerId());
            return owner == null ? null : owner.getUsername();
        }
        Team owner = teamMapper.selectById(channel.getOwnerId());
        return owner == null ? null : owner.getSlug();
    }

    private List<SearchResponse.UserHit> searchUsers(String q) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(w -> w.like(User::getUsername, q).or().like(User::getNickname, q))
                .orderByAsc(User::getUsername)
                .last("LIMIT " + LIMIT));
        List<SearchResponse.UserHit> hits = new ArrayList<>();
        for (User user : users) {
            hits.add(new SearchResponse.UserHit(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar()));
        }
        return hits;
    }

    private List<SearchResponse.ConversationHit> searchConversations(String q, Long userId) {
        List<ChatConversation> conversations = conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .like(ChatConversation::getTitle, q)
                .orderByDesc(ChatConversation::getUpdateTime)
                .last("LIMIT " + LIMIT));
        List<SearchResponse.ConversationHit> hits = new ArrayList<>();
        for (ChatConversation conversation : conversations) {
            hits.add(new SearchResponse.ConversationHit(conversation.getPublicId(), conversation.getTitle(),
                    conversation.getUpdateTime() == null ? null
                            : siteSettingService.formatDateTime(conversation.getUpdateTime()),
                    Boolean.TRUE.equals(conversation.getArchived())));
        }
        return hits;
    }
}
