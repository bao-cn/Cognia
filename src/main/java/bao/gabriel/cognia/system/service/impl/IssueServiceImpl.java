package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.issue.*;
import bao.gabriel.cognia.system.entity.*;
import bao.gabriel.cognia.system.mapper.*;
import bao.gabriel.cognia.system.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private static final Set<String> COLORS = Set.of(
            "slate", "gray", "red", "orange", "amber", "green",
            "teal", "blue", "purple", "pink");
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z][A-Za-z0-9_]{2,31})");
    private final Map<Long, Object> numberLocks = new ConcurrentHashMap<>();

    private final IssueMapper issueMapper;
    private final IssueCommentMapper commentMapper;
    private final IssueLabelMapper labelMapper;
    private final IssueLabelLinkMapper linkMapper;
    private final ChannelMapper channelMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ContributionService contributionService;
    private final SiteSettingService siteSettingService;

    @Override
    public PageResult<IssueSummaryResponse> page(Long channelId, Long viewerId, String status,
                                                  Long labelId, long page, long size) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) throw BusinessException.forbidden("无权查看该频道的工单");
        long current = Math.max(1, page);
        long pageSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<Issue> wrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getChannelId, channelId)
                .orderByDesc(Issue::getUpdateTime).orderByDesc(Issue::getId);
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!Issue.OPEN.equals(normalized) && !Issue.CLOSED.equals(normalized)) {
                throw BusinessException.badRequest("无效的工单状态");
            }
            wrapper.eq(Issue::getStatus, normalized);
        }
        if (labelId != null) {
            List<Long> ids = linkMapper.selectList(new LambdaQueryWrapper<IssueLabelLink>()
                            .eq(IssueLabelLink::getLabelId, labelId))
                    .stream().map(IssueLabelLink::getIssueId).toList();
            if (ids.isEmpty()) {
                return PageResult.empty(current, pageSize);
            }
            wrapper.in(Issue::getId, ids);
        }
        Page<Issue> result = issueMapper.selectPage(new Page<>(current, pageSize), wrapper);
        Map<Long, List<IssueLabelResponse>> labelMap = labelsByChannel(channelId);
        List<IssueSummaryResponse> records = result.getRecords().stream()
                .map(issue -> toSummary(issue, labelMap.getOrDefault(issue.getId(), List.of())))
                .toList();
        return PageResult.of(records, result.getTotal(), current, pageSize);
    }

    @Override
    public IssueDetailResponse detail(Long channelId, Long number, Long viewerId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) throw BusinessException.forbidden("无权查看该频道的工单");
        Issue issue = requireIssue(channelId, number);
        return toDetail(issue, access, viewerId);
    }

    @Override
    @Transactional
    public IssueDetailResponse create(Long channelId, Long userId, CreateIssueRequest request) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!channel.isPublic() && !access.manage()) {
            throw BusinessException.forbidden("只有公开频道或频道管理者可以提交工单");
        }
        Issue issue = new Issue();
        issue.setChannelId(channelId);
        issue.setNumber(nextNumber(channelId));
        issue.setTitle(request.title().trim());
        issue.setBody(request.body().trim());
        issue.setStatus(Issue.OPEN);
        issue.setAuthorId(userId);
        issue.setCreateTime(LocalDateTime.now());
        issue.setUpdateTime(issue.getCreateTime());
        issueMapper.insert(issue);
        return toDetail(issue, access, userId);
    }

    @Override
    @Transactional
    public IssueDetailResponse updateStatus(Long channelId, Long number, Long userId, String status) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        Issue issue = requireIssue(channelId, number);
        if (!access.manage() && !Objects.equals(issue.getAuthorId(), userId)) {
            throw BusinessException.forbidden("只有频道管理者或工单作者可以修改状态");
        }
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Issue.OPEN.equals(normalized) && !Issue.CLOSED.equals(normalized)) {
            throw BusinessException.badRequest("工单只有打开和关闭两种状态");
        }
        issue.setStatus(normalized);
        issue.setClosedAt(Issue.CLOSED.equals(normalized) ? LocalDateTime.now() : null);
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        return toDetail(issue, access, userId);
    }

    @Override
    @Transactional
    public IssueDetailResponse updateAssignee(Long channelId, Long number, Long userId, Long assigneeId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) throw BusinessException.forbidden("只有频道管理者可以分配工单");
        Issue issue = requireIssue(channelId, number);
        if (assigneeId != null && !assignees(channelId, userId).stream()
                .anyMatch(candidate -> candidate.id().equals(assigneeId))) {
            throw BusinessException.badRequest("只能分配给频道成员");
        }
        issue.setAssigneeId(assigneeId);
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        return toDetail(issue, access, userId);
    }

    @Override
    @Transactional
    public IssueDetailResponse updateLabels(Long channelId, Long number, Long userId, List<Long> labelIds) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) throw BusinessException.forbidden("只有频道管理者可以设置标签");
        Issue issue = requireIssue(channelId, number);
        Set<Long> unique = labelIds == null ? Set.of() : new LinkedHashSet<>(labelIds);
        if (!unique.isEmpty()) {
            long valid = labelMapper.selectCount(new LambdaQueryWrapper<IssueLabel>()
                    .eq(IssueLabel::getChannelId, channelId).in(IssueLabel::getId, unique));
            if (valid != unique.size()) throw BusinessException.badRequest("标签不属于该频道");
        }
        linkMapper.delete(new LambdaQueryWrapper<IssueLabelLink>().eq(IssueLabelLink::getIssueId, issue.getId()));
        for (Long labelId : unique) {
            IssueLabelLink link = new IssueLabelLink();
            link.setIssueId(issue.getId());
            link.setLabelId(labelId);
            linkMapper.insert(link);
        }
        issue.setUpdateTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        return toDetail(issue, access, userId);
    }

    @Override
    @Transactional
    public IssueCommentResponse comment(Long channelId, Long number, Long userId, CreateIssueCommentRequest request) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.read()) throw BusinessException.forbidden("无权评论该频道的工单");
        Issue issue = requireIssue(channelId, number);
        if (!issue.open()) throw BusinessException.badRequest("工单已关闭，无法继续评论");
        IssueComment quote = null;
        if (request.quoteCommentId() != null) {
            quote = commentMapper.selectById(request.quoteCommentId());
            if (quote == null || !Objects.equals(quote.getIssueId(), issue.getId())) {
                throw BusinessException.badRequest("引用的评论不存在");
            }
        }
        IssueComment comment = new IssueComment();
        comment.setIssueId(issue.getId());
        comment.setAuthorId(userId);
        comment.setBody(request.body().trim());
        comment.setQuoteCommentId(request.quoteCommentId());
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);
        issue.setUpdateTime(comment.getCreateTime());
        issueMapper.updateById(issue);
        contributionService.record(userId, "TICKET_REPLY", channelId, channel.getName(),
                Channel.OWNER_TEAM.equals(channel.getOwnerType()) ? channel.getOwnerId() : null,
                teamName(channel), 1, "issue=" + issue.getNumber());
        notifyIssue(channel, issue, userId, comment);
        return toComment(comment);
    }

    @Override
    public List<IssueLabelResponse> labels(Long channelId, Long viewerId) {
        Channel channel = requireChannel(channelId);
        if (!access(channel, viewerId).read()) throw BusinessException.forbidden("无权查看该频道的标签");
        return labelMapper.selectList(new LambdaQueryWrapper<IssueLabel>()
                        .eq(IssueLabel::getChannelId, channelId).orderByAsc(IssueLabel::getId))
                .stream().map(this::toLabel).toList();
    }

    @Override
    @Transactional
    public IssueLabelResponse createLabel(Long channelId, Long userId, CreateIssueLabelRequest request) {
        Channel channel = requireChannel(channelId);
        if (!access(channel, userId).manage()) {
            throw BusinessException.forbidden("只有频道管理者可以维护标签");
        }
        String name = request.name().trim();
        String color = request.color().trim().toLowerCase(Locale.ROOT);
        if (!COLORS.contains(color)) throw BusinessException.badRequest("不支持该标签颜色");
        long exists = labelMapper.selectCount(new LambdaQueryWrapper<IssueLabel>()
                .eq(IssueLabel::getChannelId, channelId).apply("LOWER(name) = LOWER({0})", name));
        if (exists > 0) throw BusinessException.conflict("标签名称已存在");
        IssueLabel label = new IssueLabel();
        label.setChannelId(channelId);
        label.setName(name);
        label.setColor(color);
        label.setCreateTime(LocalDateTime.now());
        labelMapper.insert(label);
        return toLabel(label);
    }

    @Override
    @Transactional
    public void deleteLabel(Long channelId, Long userId, Long labelId) {
        Channel channel = requireChannel(channelId);
        if (!access(channel, userId).manage()) {
            throw BusinessException.forbidden("只有频道管理者可以维护标签");
        }
        IssueLabel label = labelMapper.selectById(labelId);
        if (label == null || !Objects.equals(label.getChannelId(), channelId)) {
            throw BusinessException.notFound("标签不存在");
        }
        linkMapper.delete(new LambdaQueryWrapper<IssueLabelLink>().eq(IssueLabelLink::getLabelId, labelId));
        labelMapper.deleteById(labelId);
    }

    @Override
    public List<IssueUserResponse> assignees(Long channelId, Long viewerId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) throw BusinessException.forbidden("无权查看该频道的成员");
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            return List.of(toUser(userMapper.selectById(channel.getOwnerId())));
        }
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, channel.getOwnerId())
                        .orderByAsc(TeamMember::getJoinedAt))
                .stream().map(member -> userMapper.selectById(member.getUserId()))
                .filter(Objects::nonNull).map(this::toUser).toList();
    }

    private void notifyIssue(Channel channel, Issue issue, Long actorId, IssueComment comment) {
        Map<Long, String> recipients = new LinkedHashMap<>();
        if (!Objects.equals(issue.getAuthorId(), actorId)) {
            recipients.put(issue.getAuthorId(), "你创建的工单收到了新评论。");
        }
        if (issue.getAssigneeId() != null && !Objects.equals(issue.getAssigneeId(), actorId)) {
            recipients.put(issue.getAssigneeId(), "你被指派的工单收到了新评论。");
        }
        if (comment.getQuoteCommentId() != null) {
            IssueComment quote = commentMapper.selectById(comment.getQuoteCommentId());
            if (quote != null && !Objects.equals(quote.getAuthorId(), actorId)) {
                recipients.put(quote.getAuthorId(), "有人在工单中回复了你的评论。");
            }
        }
        Matcher matcher = MENTION.matcher(comment.getBody());
        while (matcher.find()) {
            User mentioned = findByUsername(matcher.group(1));
            if (mentioned != null && !Objects.equals(mentioned.getId(), actorId)) {
                recipients.put(mentioned.getId(), "你在工单评论中被提及。");
            }
        }
        User actor = userMapper.selectById(actorId);
        String actorName = displayName(actor);
        String title = "工单 #" + issue.getNumber() + " 有新回复";
        String content = "频道「" + channel.getName() + "」的工单「" + issue.getTitle() + "」中，"
                + actorName + " 回复：" + abbreviate(comment.getBody(), 180);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issueId", issue.getId());
        payload.put("issueNumber", issue.getNumber());
        payload.put("channelId", channel.getId());
        payload.put("channelSlug", channel.getSlug());
        for (Map.Entry<Long, String> entry : recipients.entrySet()) {
            Long recipient = entry.getKey();
            try {
                notificationService.notify(recipient, "ISSUE_COMMENT", title, content, payload);
            } catch (Exception e) {
                log.warn("failed to send issue notification to {}: {}", recipient, e.getMessage());
            }
            User user = userMapper.selectById(recipient);
            if (user != null && StringUtils.hasText(user.getEmail())) {
                try {
                    emailService.sendIssueNotification(user.getEmail(), channel.getName(), issue.getTitle(),
                            issue.getNumber().intValue(), actorName, entry.getValue(), abbreviate(comment.getBody(), 800));
                } catch (Exception e) {
                    log.warn("failed to send issue email to {}: {}", user.getEmail(), e.getMessage());
                }
            }
        }
    }

    private IssueDetailResponse toDetail(Issue issue, Access access, Long viewerId) {
        Map<Long, List<IssueLabelResponse>> labelMap = labelsByChannel(issue.getChannelId());
        List<IssueLabelResponse> labels = labelMap.getOrDefault(issue.getId(), List.of());
        List<IssueCommentResponse> comments = commentMapper.selectList(new LambdaQueryWrapper<IssueComment>()
                        .eq(IssueComment::getIssueId, issue.getId()).orderByAsc(IssueComment::getCreateTime))
                .stream().map(this::toComment).toList();
        return new IssueDetailResponse(issue.getId(), issue.getNumber(), issue.getTitle(), issue.getBody(),
                issue.getStatus(), toUser(userMapper.selectById(issue.getAuthorId())),
                issue.getAssigneeId() == null ? null : toUser(userMapper.selectById(issue.getAssigneeId())),
                labels, comments.size(), format(issue.getCreateTime()), format(issue.getUpdateTime()),
                access.manage(), access.manage() || Objects.equals(issue.getAuthorId(), viewerId), comments);
    }

    private IssueSummaryResponse toSummary(Issue issue, List<IssueLabelResponse> labels) {
        long count = commentMapper.selectCount(new LambdaQueryWrapper<IssueComment>()
                .eq(IssueComment::getIssueId, issue.getId()));
        return new IssueSummaryResponse(issue.getId(), issue.getNumber(), issue.getTitle(), issue.getStatus(),
                toUser(userMapper.selectById(issue.getAuthorId())),
                issue.getAssigneeId() == null ? null : toUser(userMapper.selectById(issue.getAssigneeId())),
                labels, count, format(issue.getCreateTime()), format(issue.getUpdateTime()));
    }

    private IssueCommentResponse toComment(IssueComment comment) {
        IssueComment quote = comment.getQuoteCommentId() == null ? null : commentMapper.selectById(comment.getQuoteCommentId());
        return new IssueCommentResponse(comment.getId(), toUser(userMapper.selectById(comment.getAuthorId())),
                comment.getBody(), comment.getQuoteCommentId(),
                quote == null ? null : toUser(userMapper.selectById(quote.getAuthorId())),
                quote == null ? null : quote.getBody(), format(comment.getCreateTime()));
    }

    private Map<Long, List<IssueLabelResponse>> labelsByChannel(Long channelId) {
        Map<Long, IssueLabelResponse> labels = new LinkedHashMap<>();
        labelMapper.selectList(new LambdaQueryWrapper<IssueLabel>().eq(IssueLabel::getChannelId, channelId))
                .forEach(label -> labels.put(label.getId(), toLabel(label)));
        Map<Long, List<IssueLabelResponse>> result = new LinkedHashMap<>();
        linkMapper.selectList(new LambdaQueryWrapper<IssueLabelLink>()
                        .in(!labels.isEmpty(), IssueLabelLink::getLabelId, labels.keySet()))
                .forEach(link -> {
                    IssueLabelResponse label = labels.get(link.getLabelId());
                    if (label != null) {
                        result.computeIfAbsent(link.getIssueId(), ignored -> new ArrayList<>()).add(label);
                    }
                });
        return result;
    }

    private Issue requireIssue(Long channelId, Long number) {
        Issue issue = issueMapper.selectOne(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getChannelId, channelId).eq(Issue::getNumber, number).last("LIMIT 1"));
        if (issue == null) throw BusinessException.notFound("工单不存在");
        return issue;
    }

    private Channel requireChannel(Long channelId) {
        Channel channel = channelMapper.selectById(channelId);
        if (channel == null) throw BusinessException.notFound("频道不存在");
        return channel;
    }

    private Access access(Channel channel, Long viewerId) {
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            boolean self = viewerId != null && viewerId.equals(channel.getOwnerId());
            return new Access(channel.isPublic() || self, self);
        }
        TeamMember member = viewerId == null ? null : teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, channel.getOwnerId())
                .eq(TeamMember::getUserId, viewerId).last("LIMIT 1"));
        boolean isMember = member != null;
        return new Access(channel.isPublic() || isMember, TeamMember.ROLE_OWNER.equals(member == null ? null : member.getRole()));
    }

    private long nextNumber(Long channelId) {
        synchronized (numberLocks.computeIfAbsent(channelId, ignored -> new Object())) {
            Issue last = issueMapper.selectOne(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getChannelId, channelId).orderByDesc(Issue::getNumber).last("LIMIT 1"));
            return last == null ? 1 : last.getNumber() + 1;
        }
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username).last("LIMIT 1"));
    }

    private String teamName(Channel channel) {
        if (!Channel.OWNER_TEAM.equals(channel.getOwnerType())) return null;
        Team team = teamMapper.selectById(channel.getOwnerId());
        return team == null ? null : team.getName();
    }

    private IssueUserResponse toUser(User user) {
        if (user == null) return new IssueUserResponse(null, "deleted", "已注销用户", null);
        return new IssueUserResponse(user.getId(), user.getUsername(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(), user.getAvatar());
    }

    private IssueLabelResponse toLabel(IssueLabel label) {
        return new IssueLabelResponse(label.getId(), label.getName(), label.getColor());
    }

    private String displayName(User user) {
        if (user == null) return "用户";
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : siteSettingService.formatDateTime(time);
    }

    private String abbreviate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private record Access(boolean read, boolean manage) {
    }
}
