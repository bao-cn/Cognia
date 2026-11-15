package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.service.RagCacheService;
import bao.gabriel.cognia.system.dto.recycle.AdminRecycleBinResponse;
import bao.gabriel.cognia.system.dto.recycle.RecycleBinResponse;
import bao.gabriel.cognia.system.dto.recycle.RecycleChannelOption;
import bao.gabriel.cognia.system.dto.recycle.RecycleFileContent;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RecycleBin;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.RecycleBinMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.FileDeletionService;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.RecycleBinService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RecycleBinMapper recycleBinMapper;
    private final KbDocumentMapper documentMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ChannelMapper channelMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final FileDeletionService fileDeletionService;
    private final SiteSettingService siteSettingService;
    private final MinioStorageService minioStorageService;
    private final RagCacheService ragCacheService;

    @Override
    public List<RecycleBinResponse> list(String ownerType, Long ownerId, Long viewerId) {
        String type = normalizeType(ownerType);
        assertAccess(type, ownerId, viewerId);
        return recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>()
                        .eq(RecycleBin::getOwnerType, type)
                        .eq(RecycleBin::getOwnerId, ownerId)
                        .orderByDesc(RecycleBin::getDeleteTime))
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<RecycleBinResponse> listByChannel(Long channelId) {
        return recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>()
                        .eq(RecycleBin::getChannelId, channelId)
                        .orderByDesc(RecycleBin::getDeleteTime))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void restore(Long id, Long channelId) {
        RecycleBin entry = requireEntry(id);
        if (channelId != null && !Objects.equals(entry.getChannelId(), channelId)) {
            throw BusinessException.notFound("回收站记录不属于该频道");
        }
        restoreEntry(entry);
    }

    @Override
    @Transactional
    public void purge(Long id, Long viewerId) {
        RecycleBin entry = requireEntry(id);
        assertAccess(entry.getOwnerType(), entry.getOwnerId(), viewerId);
        purgeEntry(entry);
    }

    @Override
    public PageResult<AdminRecycleBinResponse> adminPage(long page, long size, Long channelId, String keyword, String channelKeyword) {
        long current = Math.max(1, page);
        long pageSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<RecycleBin> wrapper = new LambdaQueryWrapper<RecycleBin>()
                .eq(channelId != null, RecycleBin::getChannelId, channelId)
                .like(StringUtils.hasText(keyword), RecycleBin::getName, keyword == null ? null : keyword.trim())
                .orderByDesc(RecycleBin::getDeleteTime);
        if (StringUtils.hasText(channelKeyword)) {
            String search = channelKeyword.trim();
            List<Long> matchingChannels = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                            .and(w -> w.like(Channel::getName, search).or().like(Channel::getSlug, search)))
                    .stream().map(Channel::getId).toList();
            if (matchingChannels.isEmpty()) {
                return PageResult.empty(current, pageSize);
            }
            wrapper.in(RecycleBin::getChannelId, matchingChannels);
        }
        Page<RecycleBin> result = recycleBinMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<RecycleBin> entries = result.getRecords();
        if (entries.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), current, pageSize);
        }
        Map<Long, Channel> channels = mapById(channelMapper.selectBatchIds(
                entries.stream().map(RecycleBin::getChannelId).filter(Objects::nonNull).collect(Collectors.toSet())),
                Channel::getId);
        Map<Long, User> users = mapById(userMapper.selectBatchIds(
                entries.stream().map(RecycleBin::getDeletedBy).filter(Objects::nonNull).collect(Collectors.toSet())),
                User::getId);
        Set<Long> userIds = entries.stream().filter(e -> "USER".equals(e.getOwnerType()))
                .map(RecycleBin::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> teamIds = entries.stream().filter(e -> "TEAM".equals(e.getOwnerType()))
                .map(RecycleBin::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            users.putAll(mapById(userMapper.selectBatchIds(userIds), User::getId));
        }
        Map<Long, Team> teams = teamIds.isEmpty() ? Map.of()
                : mapById(teamMapper.selectBatchIds(teamIds), Team::getId);

        List<AdminRecycleBinResponse> records = new ArrayList<>(entries.size());
        for (RecycleBin entry : entries) {
            Channel channel = channels.get(entry.getChannelId());
            User deleter = users.get(entry.getDeletedBy());
            String ownerName = "USER".equals(entry.getOwnerType())
                    ? displayName(users.get(entry.getOwnerId()))
                    : teamDisplayName(teams.get(entry.getOwnerId()));
            records.add(new AdminRecycleBinResponse(
                    entry.getId(), entry.getDocumentId(), entry.getOwnerType(), entry.getOwnerId(), ownerName,
                    entry.getChannelId(), channel == null ? null : channel.getName(),
                    channel == null ? null : channel.getSlug(), entry.getName(), entry.getSize(),
                    entry.getContentType(), entry.getDeletedBy(), displayName(deleter),
                    format(entry.getDeleteTime()), retention(entry), format(expiresAt(entry))));
        }
        return PageResult.of(records, result.getTotal(), current, pageSize);
    }

    @Override
    public List<RecycleChannelOption> adminChannels() {
        List<RecycleBin> entries = recycleBinMapper.selectList(new QueryWrapper<RecycleBin>()
                .select("DISTINCT channel_id").isNotNull("channel_id"));
        if (entries.isEmpty()) {
            return List.of();
        }
        Set<Long> channelIds = entries.stream().map(RecycleBin::getChannelId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Channel> channels = mapById(channelMapper.selectBatchIds(channelIds), Channel::getId);
        Set<Long> userIds = channels.values().stream().filter(c -> "USER".equals(c.getOwnerType()))
                .map(Channel::getOwnerId).collect(Collectors.toSet());
        Set<Long> teamIds = channels.values().stream().filter(c -> "TEAM".equals(c.getOwnerType()))
                .map(Channel::getOwnerId).collect(Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : mapById(userMapper.selectBatchIds(userIds), User::getId);
        Map<Long, Team> teams = teamIds.isEmpty() ? Map.of() : mapById(teamMapper.selectBatchIds(teamIds), Team::getId);
        return channels.values().stream()
                .map(c -> new RecycleChannelOption(c.getId(), c.getName(), c.getSlug(), c.getOwnerType(),
                        "USER".equals(c.getOwnerType()) ? displayName(users.get(c.getOwnerId()))
                                : teamDisplayName(teams.get(c.getOwnerId()))))
                .sorted(Comparator.comparing(RecycleChannelOption::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    public RecycleFileContent adminContent(Long id) {
        RecycleBin entry = requireEntry(id);
        String key = StringUtils.hasText(entry.getObjectKey()) ? entry.getObjectKey() : entry.getOriginalObjectKey();
        if (!StringUtils.hasText(key)) {
            throw BusinessException.notFound("文件不存在");
        }
        try {
            byte[] bytes = minioStorageService.readObject(key);
            if (bytes == null || bytes.length == 0) {
                throw BusinessException.notFound("文件不存在");
            }
            return new RecycleFileContent(entry.getName(), entry.getContentType(), bytes);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Unable to read recycle object {}: {}", key, ex.getMessage());
            throw BusinessException.notFound("文件不存在或已被清理");
        }
    }

    @Override
    @Transactional
    public void adminRestore(Long id) {
        restoreEntry(requireEntry(id));
    }

    @Override
    @Transactional
    public void adminPurge(Long id) {
        purgeEntry(requireEntry(id));
    }

    private void restoreEntry(RecycleBin entry) {
        KbDocument document = entry.getDocumentId() == null ? null : documentMapper.selectById(entry.getDocumentId());
        if (document == null) {
            throw BusinessException.notFound("原文档不存在，无法恢复");
        }
        document.setStatus("READY");
        document.setErrorMessage(null);
        document.setProcessedAt(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        recycleBinMapper.deleteById(entry.getId());
        try {
            ragCacheService.invalidateDocument(document.getId());
        } catch (Exception ex) {
            log.warn("Unable to invalidate retrieval cache for restored document {}: {}", document.getId(), ex.getMessage());
        }
    }

    private void purgeEntry(RecycleBin entry) {
        KbDocument document = entry.getDocumentId() == null ? null : documentMapper.selectById(entry.getDocumentId());
        if (document != null) {
            fileDeletionService.permanentDelete(document);
            return;
        }
        if (entry.getObjectKey() != null) {
            fileDeletionService.deleteObjectQuietly(entry.getObjectKey());
        }
        if (entry.getOriginalObjectKey() != null) {
            fileDeletionService.deleteObjectQuietly(entry.getOriginalObjectKey());
        }
        recycleBinMapper.deleteById(entry.getId());
    }

    private RecycleBin requireEntry(Long id) {
        RecycleBin entry = id == null ? null : recycleBinMapper.selectById(id);
        if (entry == null) {
            throw BusinessException.notFound("回收站记录不存在");
        }
        return entry;
    }

    private void assertAccess(String ownerType, Long ownerId, Long viewerId) {
        if (viewerId == null) {
            throw BusinessException.forbidden("请先登录");
        }
        if ("USER".equals(ownerType)) {
            if (!viewerId.equals(ownerId)) {
                throw BusinessException.forbidden("无权访问该回收站");
            }
            return;
        }
        Long membership = teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, ownerId)
                .eq(TeamMember::getUserId, viewerId));
        if (membership == null || membership == 0) {
            throw BusinessException.forbidden("无权访问该团队回收站");
        }
    }

    private RecycleBinResponse toResponse(RecycleBin entry) {
        int retention = retention(entry);
        return new RecycleBinResponse(
                entry.getId(), entry.getDocumentId(), entry.getOwnerType(), entry.getOwnerId(),
                entry.getChannelId(), entry.getName(), entry.getSize(), entry.getContentType(),
                entry.getDeletedBy(), format(entry.getDeleteTime()), retention, format(expiresAt(entry)));
    }

    private int retention(RecycleBin entry) {
        return entry.getRetentionDays() == null
                ? siteSettingService.getSetting().recycleRetentionDays()
                : entry.getRetentionDays();
    }

    private LocalDateTime expiresAt(RecycleBin entry) {
        return entry.getDeleteTime() == null ? null : entry.getDeleteTime().plusDays(retention(entry));
    }

    private String normalizeType(String ownerType) {
        String type = ownerType == null ? "" : ownerType.trim().toUpperCase();
        if (!"USER".equals(type) && !"TEAM".equals(type)) {
            throw BusinessException.badRequest("归属类型不正确");
        }
        return type;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : FORMATTER.format(value);
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String teamDisplayName(Team team) {
        return team == null ? null : team.getName();
    }

    private <T> Map<Long, T> mapById(Collection<T> values, Function<T, Long> id) {
        Map<Long, T> map = new HashMap<>();
        if (values != null) {
            for (T value : values) {
                map.put(id.apply(value), value);
            }
        }
        return map;
    }
}
