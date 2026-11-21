package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.storage.UploadProgressListener;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.ingestion.IngestionQueueService;
import bao.gabriel.cognia.common.util.ReservedNames;
import bao.gabriel.cognia.system.dto.channel.ChannelAboutResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelDetailResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelDocumentResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelSummaryResponse;
import bao.gabriel.cognia.system.dto.channel.CreateChannelRequest;
import bao.gabriel.cognia.system.dto.channel.CreateFolderRequest;
import bao.gabriel.cognia.system.dto.channel.FolderResponse;
import bao.gabriel.cognia.system.dto.channel.UpdateChannelRequest;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.ChannelNotifySubscription;
import bao.gabriel.cognia.system.entity.ChannelSubscription;
import bao.gabriel.cognia.system.entity.Folder;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.ChannelNotifySubscriptionMapper;
import bao.gabriel.cognia.system.mapper.ChannelSubscriptionMapper;
import bao.gabriel.cognia.system.mapper.FolderMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.DocumentIngestionService;
import bao.gabriel.cognia.system.service.DocumentStateMachine;
import bao.gabriel.cognia.system.service.ContributionService;
import bao.gabriel.cognia.system.service.FileDeletionService;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.TwoFactorService;
import bao.gabriel.cognia.system.service.UserService;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private static final Pattern OWNER_TYPE = Pattern.compile("^(USER|TEAM)$");
    private static final Pattern MARKDOWN_ASSET = Pattern.compile(
            "(!?\\[[^\\]]*\\]\\()(\\s*)((?:\\./|/)?assets/[^)\\s]+)(\\s*\\))");
    private static final Pattern HTML_ASSET = Pattern.compile(
            "(<img\\b[^>]*\\bsrc=[\"'])((?:\\./|/)?assets/[^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> WRITE_ROLES = Set.of(
            TeamMember.ROLE_OWNER, TeamMember.ROLE_MANAGER, TeamMember.ROLE_EDITOR,
            "MAINTAINER");

    private final ChannelMapper channelMapper;
    private final ChannelSubscriptionMapper subscriptionMapper;
    private final ChannelNotifySubscriptionMapper notifySubscriptionMapper;
    private final FolderMapper folderMapper;
    private final KbDocumentMapper documentMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final MinioStorageService storageService;
    private final DocumentIngestionService ingestionService;
    private final FileDeletionService deletionService;
    private final DocumentStateMachine stateMachine;
    private final SiteSettingService siteSettingService;
    private final UserService userService;
    private final TwoFactorService twoFactorService;
    private final EmbeddingService embeddingService;
    private final IngestionQueueService ingestionQueueService;
    private final JdbcTemplate jdbcTemplate;
    private final ContributionService contributionService;

    @Override
    public List<ChannelSummaryResponse> list(String ownerType, Long ownerId, Long viewerId) {
        String type = normalizeType(ownerType);
        if (ownerId == null) {
            throw BusinessException.badRequest("频道归属不能为空");
        }
        Access access = accessForOwner(type, ownerId, viewerId);
        return channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                        .eq(Channel::getOwnerType, type)
                        .eq(Channel::getOwnerId, ownerId)
                        .eq(!access.member(), Channel::getIsPublic, true)
                        .orderByDesc(Channel::getPinned)
                        .orderByAsc(Channel::getPinnedOrder)
                        .orderByDesc(Channel::getUpdateTime))
                .stream().map(channel -> toSummary(channel, viewerId)).toList();
    }

    @Override
    public ChannelDetailResponse detail(Long channelId, Long viewerId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) {
            throw BusinessException.forbidden(channel.archived()
                    ? "频道已存档，暂无法访问"
                    : !channel.isPublic() ? "频道已转为私密，暂无法访问" : "无权访问该频道");
        }
        List<FolderResponse> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getChannelId, channelId)
                        .orderByAsc(Folder::getDepth).orderByAsc(Folder::getId))
                .stream().map(this::toFolder).toList();
        List<ChannelDocumentResponse> documents = documents(channelId, viewerId, null);
        return new ChannelDetailResponse(toSummary(channel, viewerId), folders, documents,
                access.write(), access.manage(), access.member() || access.write(),
                access.member() || access.write(), about(channel, documents));
    }

    @Override
    @Transactional
    public ChannelSummaryResponse create(Long userId, CreateChannelRequest request) {
        String type = normalizeType(request.ownerType());
        Long ownerId = request.ownerId();
        if (Channel.OWNER_USER.equals(type)) {
            ownerId = userId;
        }
        if (ownerId == null) {
            throw BusinessException.badRequest("请选择频道归属");
        }
        Access access = accessForOwner(type, ownerId, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有在该归属下创建频道的权限");
        }
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        if (ReservedNames.isReserved(slug)) {
            throw BusinessException.badRequest("频道标识是系统保留字");
        }
        long exists = channelMapper.selectCount(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getOwnerType, type).eq(Channel::getOwnerId, ownerId)
                .eq(Channel::getSlug, slug));
        if (exists > 0) {
            throw BusinessException.conflict("该归属下已存在同名频道");
        }
        LocalDateTime now = LocalDateTime.now();
        Channel channel = new Channel();
        channel.setOwnerType(type);
        channel.setOwnerId(ownerId);
        channel.setName(request.name().trim());
        channel.setSlug(slug);
        channel.setDescription(trimToNull(request.description()));
        channel.setIsPublic(Boolean.TRUE.equals(request.isPublic()));
        channel.setPinned(false);
        channel.setPinnedOrder(0);
        channel.setFeatured(false);
        channel.setWebSearchEnabled(true);
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        channelMapper.insert(channel);
        contributionService.record(userId, "CHANNEL_CREATE", channel.getId(), channel.getName(),
                teamIdOf(channel), teamNameOf(channel), 1, null);
        return toSummary(channel);
    }

    @Override
    @Transactional
    public ChannelSummaryResponse update(Long userId, Long channelId, UpdateChannelRequest request) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) {
            throw BusinessException.forbidden("只有频道归属者可以修改频道设置");
        }
        channel.setName(request.name().trim());
        channel.setDescription(trimToNull(request.description()));
        if (request.webSearchEnabled() != null) {
            channel.setWebSearchEnabled(request.webSearchEnabled());
        }
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toSummary(channel);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long channelId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) {
            throw BusinessException.forbidden("只有频道归属者可以删除频道");
        }
        List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channelId)
                .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
        for (KbDocument document : documents) {
            deletionService.logicalDelete(document, userId);
        }
        folderMapper.delete(new LambdaQueryWrapper<Folder>().eq(Folder::getChannelId, channelId));
        contributionService.record(userId, "CHANNEL_DELETE", channelId, channel.getName(),
                teamIdOf(channel), teamNameOf(channel), 1, null);
        channelMapper.deleteById(channelId);
    }

    @Override
    @Transactional
    public ChannelSummaryResponse setPinned(Long userId, Long channelId, boolean pinned) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) {
            throw BusinessException.forbidden("只有频道归属者可以置顶频道");
        }
        if (pinned && !Boolean.TRUE.equals(channel.getPinned())) {
            long pinnedCount = channelMapper.selectCount(new LambdaQueryWrapper<Channel>()
                    .eq(Channel::getOwnerType, channel.getOwnerType())
                    .eq(Channel::getOwnerId, channel.getOwnerId())
                    .eq(Channel::getPinned, true));
            if (pinnedCount >= 4) {
                throw BusinessException.badRequest("最多只能置顶 4 个频道");
            }
            Integer maxOrder = channelMapper.selectList(new LambdaQueryWrapper<Channel>()
                            .eq(Channel::getOwnerType, channel.getOwnerType())
                            .eq(Channel::getOwnerId, channel.getOwnerId())
                            .eq(Channel::getPinned, true))
                    .stream().map(Channel::getPinnedOrder).filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo).orElse(0);
            channel.setPinned(true);
            channel.setPinnedOrder(maxOrder + 1);
        } else if (!pinned) {
            channel.setPinned(false);
            channel.setPinnedOrder(0);
        }
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toSummary(channel);
    }

    @Override
    @Transactional
    public FolderResponse createFolder(Long userId, Long channelId, CreateFolderRequest request) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有在该频道创建文件夹的权限");
        }
        ensureNotArchived(channel, "频道已存档，取消存档后才能新建文件夹");
        Folder parent = null;
        int depth = 1;
        if (request.parentId() != null) {
            parent = folderMapper.selectById(request.parentId());
            if (parent == null || !channelId.equals(parent.getChannelId())) {
                throw BusinessException.badRequest("父文件夹不存在");
            }
            depth = parent.getDepth() + 1;
        }
        int maxDepth = siteSettingService.getSetting().maxFolderDepth();
        if (depth > maxDepth) {
            throw BusinessException.badRequest("文件夹最多支持 " + maxDepth + " 层");
        }
        String name = request.name().trim();
        long sameName = folderMapper.selectCount(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getChannelId, channelId)
                .eq(Folder::getName, name)
                .isNull(request.parentId() == null, Folder::getParentId)
                .eq(request.parentId() != null, Folder::getParentId, request.parentId()));
        if (sameName > 0) {
            throw BusinessException.conflict("同级目录下已存在同名文件夹");
        }
        Folder folder = new Folder();
        folder.setChannelId(channelId);
        folder.setParentId(request.parentId());
        folder.setName(name);
        folder.setDepth(depth);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.insert(folder);
        return toFolder(folder);
    }

    @Override
    @Transactional
    public void deleteFolder(Long userId, Long channelId, Long folderId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有删除文件夹的权限");
        }
        ensureNotArchived(channel, "频道已存档，取消存档后才能删除文件夹");
        Folder folder = folderMapper.selectById(folderId);
        if (folder == null || !channelId.equals(folder.getChannelId())) {
            throw BusinessException.notFound("文件夹不存在");
        }
        long children = folderMapper.selectCount(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getParentId, folderId));
        long docs = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getFolderId, folderId)
                .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
        if (children > 0 || docs > 0) {
            throw BusinessException.badRequest("请先清空文件夹");
        }
        folderMapper.deleteById(folderId);
    }

    @Override
    @Transactional
    public ChannelDocumentResponse uploadDocument(Long userId, Long channelId, Long folderId, MultipartFile file,
                                                  String title, String documentType, String expirationTime, String uploadBatchId) {
        ingestionQueueService.ensureCapacity();
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有上传文档的权限");
        }
        ensureNotArchived(channel, "频道已存档，取消存档后才能上传文件");
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的文档");
        }
        Folder folder = null;
        if (folderId != null) {
            folder = folderMapper.selectById(folderId);
            if (folder == null || !channelId.equals(folder.getChannelId())) {
                throw BusinessException.badRequest("文件夹不存在");
            }
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim() : "未命名文档";
        String base = "kb/" + ownerHandle(channel) + "/" + channel.getSlug();
        if (folder != null) {
            base += "/" + folderPath(folder);
        }
        String extension = extensionOf(originalName);
        String markdownKind = folder == null ? markdownKind(originalName) : null;
        boolean asset = folder != null && isRootAssetsFolder(folder);
        boolean skipIngestion = markdownKind != null || asset;
        String key = base + "/" + (skipIngestion
                ? safeObjectName(originalName, extension)
                : UUID.randomUUID().toString().replace("-", "") + (extension.isEmpty() ? "" : "." + extension));
        if (asset) {
            long duplicates = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                    .eq(KbDocument::getChannelId, channelId)
                    .eq(KbDocument::getFolderId, folderId)
                    .eq(KbDocument::getName, originalName)
                    .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
            if (duplicates > 0) {
                throw BusinessException.conflict("assets 目录下已存在同名文件");
            }
        }
        KbDocument existingMarkdown = markdownKind == null
                ? null : findRootMarkdown(channelId, "AGENT".equals(markdownKind));
        String previousKey = existingMarkdown == null ? null : existingMarkdown.getObjectKey();
        StoredObjectResponse stored = (asset || markdownKind != null)
                ? storageService.uploadAssetAtKey(file, key, UploadProgressListener.NOOP)
                : storageService.uploadAtKey(file, key, UploadProgressListener.NOOP);
        LocalDateTime now = LocalDateTime.now();
        KbDocument document = existingMarkdown == null ? new KbDocument() : existingMarkdown;
        if (existingMarkdown == null) {
            document.setOwnerType(channel.getOwnerType());
            document.setOwnerId(channel.getOwnerId());
            document.setChannelId(channelId);
            document.setFolderId(folderId);
            document.setCreatedBy(userId);
            document.setUploadBatchId(StringUtils.hasText(uploadBatchId) ? uploadBatchId.trim() : null);
            document.setCreateTime(now);
            document.setVersion(1);
        } else if (previousKey != null && !previousKey.equals(stored.key())) {
            deleteObjectQuietly(previousKey);
            document.setVersion(document.getVersion() == null ? 2 : document.getVersion() + 1);
        }
        document.setName(originalName);
        document.setTitle(StringUtils.hasText(title) ? title.trim() : originalName);
        document.setDocumentType(StringUtils.hasText(documentType) ? documentType.trim() : extension);
        document.setAuthor(ownerHandle(channel));
        document.setExpirationTime(parseDateTime(expirationTime));
        document.setObjectKey(stored.key());
        document.setOriginalKey(stored.key());
        document.setSize(stored.size());
        document.setContentType(stored.contentType());
        document.setRetryCount(0);
        document.setErrorMessage(null);
        document.setUpdateTime(now);
        if (skipIngestion) {
            document.setStatus(DocumentStateMachine.DocumentStatus.READY.name());
            document.setProcessedAt(now);
        } else {
            document.setStatus(DocumentStateMachine.DocumentStatus.PENDING.name());
            document.setProcessedAt(null);
        }
        if (existingMarkdown == null) {
            documentMapper.insert(document);
        } else {
            documentMapper.updateById(document);
        }
        if (!skipIngestion) {
            ingestionService.ingest(document.getId());
        }
        if (existingMarkdown == null && !skipIngestion) {
            contributionService.record(userId, "DOC_CREATE", channelId, channel.getName(),
                    teamIdOf(channel), teamNameOf(channel), 1, document.getName());
        } else if (existingMarkdown != null) {
            contributionService.record(userId, "DOC_UPDATE", channelId, channel.getName(),
                    teamIdOf(channel), teamNameOf(channel), 1, document.getName());
        }
        KbDocument saved = documentMapper.selectById(document.getId());
        return toDocument(saved == null ? document : saved);
    }

    @Override
    public String readMarkdown(Long channelId, Long viewerId, String kind) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) {
            throw BusinessException.forbidden(channel.archived()
                    ? "频道已存档，暂无法访问"
                    : !channel.isPublic() ? "频道已转为私密，暂无法访问" : "无权访问该频道");
        }
        boolean agent = "AGENT".equalsIgnoreCase(kind);
        return readMarkdownContent(channel, agent);
    }

    @Override
    public String readDocumentMarkdown(Long channelId, Long documentId, Long viewerId) {
        Channel channel = requireChannel(channelId);
        if (!access(channel, viewerId).read()) {
            throw BusinessException.forbidden(channel.archived()
                    ? "频道已存档，暂无法访问"
                    : !channel.isPublic() ? "频道已转为私密，暂无法访问" : "无权访问该频道");
        }
        KbDocument document = documentId == null ? null : documentMapper.selectById(documentId);
        if (document == null || !channelId.equals(document.getChannelId())
                || DocumentStateMachine.DocumentStatus.DELETED.name().equals(document.getStatus())) {
            throw BusinessException.notFound("文档不存在");
        }
        if (document.getFolderId() == null && "readme".equals(markdownBaseName(document.getName()))) {
            return readMarkdownContent(channel, false);
        }
        try {
            return new String(storageService.readObject(document.getObjectKey()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            log.warn("Unable to read markdown document {}: {}", documentId, ex.getMessage());
            throw BusinessException.notFound("文档内容不可用");
        }
    }

    @Override
    public boolean webSearchEnabled(Long channelId) {
        Channel channel = channelMapper.selectById(channelId);
        return channel == null || channel.webSearchEnabled();
    }

    @Override
    public String agentPrompt(List<Long> channelIds, Long viewerId) {
        if (channelIds == null || channelIds.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        for (Long channelId : channelIds) {
            if (channelId == null) {
                continue;
            }
            Channel channel = channelMapper.selectById(channelId);
            if (channel == null || !access(channel, viewerId).read()) {
                continue;
            }
            String content = readMarkdownContent(channel, true);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            int remaining = 12000 - prompt.length();
            if (remaining <= 0) {
                break;
            }
            String block = "## 频道 " + channel.getName() + " (" + channel.getSlug() + ")\n" + content.trim();
            if (prompt.length() > 0) {
                block = "\n\n" + block;
            }
            if (block.length() > remaining) {
                block = block.substring(0, remaining);
            }
            prompt.append(block);
        }
        return prompt.toString();
    }

    @Override
    public boolean hasWriteAccess(Long channelId, Long userId) {
        Channel channel = channelMapper.selectById(channelId);
        return channel != null && access(channel, userId).write();
    }

    @Override
    public boolean hasNotificationAccess(Long channelId, Long userId) {
        Channel channel = channelMapper.selectById(channelId);
        if (channel == null) {
            return false;
        }
        Access access = access(channel, userId);
        return access.member() || access.write();
    }

    @Override
    @Transactional
    public ChannelSummaryResponse setWebSearchEnabled(Long userId, Long channelId, boolean enabled) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) {
            throw BusinessException.forbidden("你没有管理该频道网络搜索开关的权限");
        }
        channel.setWebSearchEnabled(enabled);
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toSummary(channel, userId);
    }

    private String readMarkdownContent(Channel channel, boolean agent) {
        KbDocument rootDocument = findRootMarkdown(channel.getId(), agent);
        String key = rootDocument == null ? null : rootDocument.getObjectKey();
        if (!StringUtils.hasText(key)) {
            return "";
        }
        try {
            String content = new String(storageService.readObject(key), StandardCharsets.UTF_8);
            return agent ? content : rewriteReadmeAssets(channel, content);
        } catch (RuntimeException ex) {
            log.warn("Unable to read channel markdown for channel {}: {}", channel.getId(), ex.getMessage());
            return "";
        }
    }

    /**
     * Root-folder documents whose basename is readme/agent override the key
     * stored on the channel row. Matching ignores case and the file extension.
     */
    private KbDocument findRootMarkdown(Long channelId, boolean agent) {
        String statusDeleted = DocumentStateMachine.DocumentStatus.DELETED.name();
        List<KbDocument> candidates = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channelId)
                .isNull(KbDocument::getFolderId)
                .ne(KbDocument::getStatus, statusDeleted)
                .orderByAsc(KbDocument::getId));
        String wanted = agent ? "agent" : "readme";
        return candidates.stream()
                .filter(doc -> wanted.equals(markdownBaseName(doc.getName())))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ChannelDocumentResponse> documents(Long channelId, Long viewerId, Long folderId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) {
            throw BusinessException.forbidden(channel.archived()
                    ? "频道已存档，暂无法访问"
                    : !channel.isPublic() ? "频道已转为私密，暂无法访问" : "无权访问该频道");
        }
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getChannelId, channelId)
                        .eq(folderId != null, KbDocument::getFolderId, folderId)
                        .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name())
                        .orderByDesc(KbDocument::getCreateTime))
                .stream().map(this::toDocument).toList();
    }

    @Override
    public DocumentContent documentContent(Long documentId, Long viewerId) {
        KbDocument document = documentId == null ? null : documentMapper.selectById(documentId);
        if (document == null || DocumentStateMachine.DocumentStatus.DELETED.name().equals(document.getStatus())) {
            throw BusinessException.notFound("文档不存在");
        }
        Channel channel = requireChannel(document.getChannelId());
        if (!access(channel, viewerId).read()) {
            throw BusinessException.notFound("文档不存在");
        }
        try {
            byte[] bytes = storageService.readObject(document.getObjectKey());
            String contentType = StringUtils.hasText(document.getContentType())
                    ? document.getContentType() : "application/octet-stream";
            return new DocumentContent(document.getName(), contentType, bytes);
        } catch (RuntimeException ex) {
            log.warn("Unable to read document {}: {}", documentId, ex.getMessage());
            throw BusinessException.notFound("文档内容不可用");
        }
    }

    @Override
    @Transactional
    public void deleteDocument(Long userId, Long channelId, Long documentId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有删除文档的权限");
        }
        ensureNotArchived(channel, "频道已存档，取消存档后才能删除文档");
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || !channelId.equals(document.getChannelId())) {
            throw BusinessException.notFound("文档不存在");
        }
        deletionService.logicalDelete(document, userId);
        contributionService.record(userId, "DOC_DELETE", channelId, channel.getName(),
                teamIdOf(channel), teamNameOf(channel), 1, document.getName());
    }

    @Override
    public ChannelDocumentResponse moveDocument(Long userId, Long channelId, Long documentId, Long folderId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有移动文档的权限");
        }
        ensureNotArchived(channel, "频道已存档，取消存档后才能移动文档");
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || !channelId.equals(document.getChannelId())
                || DocumentStateMachine.DocumentStatus.DELETED.name().equals(document.getStatus())) {
            throw BusinessException.notFound("文档不存在");
        }
        if (document.getFolderId() == null && markdownKind(document.getName()) != null) {
            throw BusinessException.badRequest("README 和 AGENT 文件必须保留在频道根目录");
        }
        if (folderId != null) {
            Folder target = folderMapper.selectById(folderId);
            if (target == null || !channelId.equals(target.getChannelId())) {
                throw BusinessException.notFound("目标文件夹不存在");
            }
        }
        document.setFolderId(folderId);
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        return toDocument(document);
    }

    @Override
    @Transactional
    public int moveDocuments(Long userId, Long channelId, List<Long> documentIds, Long folderId) {
        int moved = 0;
        for (Long documentId : documentIds) {
            moveDocument(userId, channelId, documentId, folderId);
            moved++;
        }
        return moved;
    }

    @Override
    public void retryDocument(Long userId, Long channelId, Long documentId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有重试文档的权限");
        }
        ensureNotArchived(channel, "频道已存档，取消存档后才能重试文档");
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || !channelId.equals(document.getChannelId())) {
            throw BusinessException.notFound("文档不存在");
        }
        ingestionQueueService.cancelDocument(documentId);
        document.setStatus("PENDING");
        document.setErrorMessage(null);
        document.setProcessedAt(null);
        document.setRetryCount(document.getRetryCount() == null ? 1 : document.getRetryCount() + 1);
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(document);
        ingestionService.ingest(document.getId());
    }


    @Override
    @Transactional
    public ChannelSummaryResponse setVisibility(Long userId, Long channelId, boolean isPublic, String verificationCode) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) {
            throw BusinessException.forbidden("只有频道归属者可以修改频道可见性");
        }
        if (channel.isPublic() == isPublic) {
            return toSummary(channel, userId);
        }
        if (isPublic && !channel.isPublic()) {
            requireDangerVerification(userId, verificationCode);
        }
        channel.setIsPublic(isPublic);
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toSummary(channel, userId);
    }

    @Override
    @Transactional
    public ChannelSummaryResponse setArchived(Long userId, Long channelId, boolean archived, String verificationCode) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) {
            throw BusinessException.forbidden("只有频道归属者可以存档频道");
        }
        if (channel.archived() == archived) {
            return toSummary(channel, userId);
        }
        requireDangerVerification(userId, verificationCode);
        channel.setArchived(archived);
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toSummary(channel, userId);
    }

    private void ensureNotArchived(Channel channel, String message) {
        if (channel.archived()) {
            throw BusinessException.forbidden(message);
        }
    }

    private void requireDangerVerification(Long userId, String verificationCode) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            if (!StringUtils.hasText(verificationCode)) {
                throw BusinessException.badRequest("请输入两步验证码");
            }
            if (!twoFactorService.verify(user, verificationCode)) {
                throw BusinessException.badRequest("两步验证码不正确");
            }
            return;
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw BusinessException.badRequest("当前账号未设置密码，请先设置密码或开启两步验证");
        }
        if (!StringUtils.hasText(verificationCode)) {
            throw BusinessException.badRequest("请输入登录密码");
        }
        if (!userService.matchesPassword(userId, verificationCode)) {
            throw BusinessException.badRequest("密码不正确");
        }
    }

    private ChannelAboutResponse about(Channel channel, List<ChannelDocumentResponse> documents) {
        long subscriberCount = subscriptionMapper.selectCount(new LambdaQueryWrapper<ChannelSubscription>()
                .eq(ChannelSubscription::getChannelId, channel.getId()));
        long notifyCount = notifySubscriptionMapper.selectCount(new LambdaQueryWrapper<ChannelNotifySubscription>()
                .eq(ChannelNotifySubscription::getChannelId, channel.getId()));
        KbDocument readme = findRootMarkdown(channel.getId(), false);
        String readmeDocumentId = readme == null ? null : String.valueOf(readme.getId());
        String readmeName = readme == null ? null : readme.getName();
        boolean hasReadme = readme != null;

        List<ChannelAboutResponse.Contributor> contributors = new ArrayList<>();
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            User owner = userMapper.selectById(channel.getOwnerId());
            if (owner != null) {
                contributors.add(contributor(owner, documents.size()));
            }
        } else {
            Map<Long, Long> counts = new LinkedHashMap<>();
            List<KbDocument> entities = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                    .eq(KbDocument::getChannelId, channel.getId())
                    .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
            for (KbDocument entity : entities) {
                if (entity.getCreatedBy() != null) {
                    counts.merge(entity.getCreatedBy(), 1L, Long::sum);
                }
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                    .limit(12)
                    .forEach(entry -> {
                        User user = userMapper.selectById(entry.getKey());
                        if (user != null) {
                            contributors.add(contributor(user, entry.getValue()));
                        }
                    });
        }

        Map<String, long[]> byType = new LinkedHashMap<>();
        for (ChannelDocumentResponse document : documents) {
            String type = normalizedDocumentType(document);
            long[] stat = byType.computeIfAbsent(type, key -> new long[2]);
            stat[0] += 1;
            stat[1] += document.size();
        }
        List<ChannelAboutResponse.FileType> fileTypes = byType.entrySet().stream()
                .map(entry -> new ChannelAboutResponse.FileType(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(ChannelAboutResponse.FileType::count).reversed()
                        .thenComparing(ChannelAboutResponse.FileType::type))
                .toList();

        long totalChunks = countChunks(channel.getId());
        long readyChunks = countReadyChunks(channel.getId());
        boolean embeddingReady = totalChunks == 0 || readyChunks >= totalChunks;
        long pendingChunks = Math.max(0, totalChunks - readyChunks);
        if (channel.reembedding() && pendingChunks == 0) {
            channel.setReembedding(false);
            channelMapper.updateById(channel);
        }
        return new ChannelAboutResponse(channel.getDescription(), hasReadme, readmeDocumentId, readmeName,
                channel.archived(), subscriberCount, notifyCount, embeddingReady, pendingChunks,
                channel.reembedding(), contributors, fileTypes);
    }

    @Override
    public Map<Long, Boolean> embeddingReadiness(List<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Boolean> result = new LinkedHashMap<>();
        channelIds.stream().filter(Objects::nonNull).distinct().limit(50).forEach(id -> {
            long total = countChunks(id);
            long ready = countReadyChunks(id);
            result.put(id, total == 0 || ready >= total);
        });
        return result;
    }

    private long countChunks(Long channelId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk dc JOIN kb_document d ON d.id = dc.document_id
                WHERE d.channel_id = ? AND d.status <> 'DELETED'
                """, Long.class, channelId);
        return value == null ? 0 : value;
    }

    private long countReadyChunks(Long channelId) {
        String label = embeddingService.activeModelLabel();
        if (label == null) return 0;
        Long value = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk dc JOIN kb_document d ON d.id = dc.document_id
                WHERE d.channel_id = ? AND d.status <> 'DELETED'
                  AND dc.embedding IS NOT NULL AND dc.embedding_status = 'READY' AND dc.embedding_model = ?
                """, Long.class, channelId, label);
        return value == null ? 0 : value;
    }

    @Override
    @Transactional
    public int reembedAll(Long channelId, Long userId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) throw BusinessException.forbidden("只有频道管理者可以重新向量化");
        List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channelId)
                .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
        if (documents.isEmpty()) return 0;
        if (channel.reembedding()) {
            throw BusinessException.conflict("频道正在重新向量化，请等待当前任务完成");
        }
        channel.setReembedding(true);
        channelMapper.updateById(channel);
        jdbcTemplate.update("""
                UPDATE document_chunk SET embedding = NULL, embedding_status = 'PENDING'
                WHERE document_id IN (SELECT id FROM kb_document WHERE channel_id = ? AND status <> 'DELETED')
                """, channelId);
        int queued = 0;
        for (KbDocument document : documents) {
            if (ingestionQueueService.enqueue(document.getId(), channelId, "EMBED")) queued++;
        }
        return queued;
    }

    @Override
    public int cancelReembed(Long channelId, Long userId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.manage()) throw BusinessException.forbidden("只有频道管理者可以取消向量化");
        int cancelled = ingestionQueueService.cancelChannel(channelId);
        channel.setReembedding(false);
        channelMapper.updateById(channel);
        jdbcTemplate.update("""
                UPDATE kb_document d SET status = 'FAILED', error_message = '已取消向量化'
                WHERE d.channel_id = ? AND d.status <> 'DELETED'
                  AND EXISTS (SELECT 1 FROM document_chunk dc
                              WHERE dc.document_id = d.id AND dc.embedding_status = 'PENDING')
                """, channelId);
        return cancelled;
    }

    private ChannelAboutResponse.Contributor contributor(User user, long count) {
        return new ChannelAboutResponse.Contributor(
                String.valueOf(user.getId()), user.getUsername(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(),
                user.getAvatar(), count);
    }

    private String normalizedDocumentType(ChannelDocumentResponse document) {
        String type = document.documentType();
        if (!StringUtils.hasText(type)) {
            type = extensionOf(document.name());
        }
        if (!StringUtils.hasText(type)) {
            return "other";
        }
        return type.toLowerCase(Locale.ROOT).replaceAll("^.*/", "");
    }

    private Channel requireChannel(Long channelId) {
        Channel channel = channelId == null ? null : channelMapper.selectById(channelId);
        if (channel == null) {
            throw BusinessException.notFound("频道不存在");
        }
        return channel;
    }

    private String normalizeType(String ownerType) {
        if (ownerType == null || !OWNER_TYPE.matcher(ownerType.toUpperCase(Locale.ROOT)).matches()) {
            throw BusinessException.badRequest("频道归属类型不正确");
        }
        return ownerType.toUpperCase(Locale.ROOT);
    }

    private Access accessForOwner(String ownerType, Long ownerId, Long viewerId) {
        if (Channel.OWNER_USER.equals(ownerType)) {
            boolean self = viewerId != null && viewerId.equals(ownerId);
            return new Access(self, self, self, self);
        }
        TeamMember membership = viewerId == null ? null : memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, ownerId).eq(TeamMember::getUserId, viewerId).last("LIMIT 1"));
        boolean member = membership != null;
        String role = membership == null ? null : membership.getRole();
        boolean write = member && WRITE_ROLES.contains(role);
        boolean manage = TeamMember.ROLE_OWNER.equals(role);
        return new Access(member, write, manage, member);
    }

    private Access access(Channel channel, Long viewerId) {
        if (Channel.OWNER_USER.equals(channel.getOwnerType())) {
            boolean self = viewerId != null && viewerId.equals(channel.getOwnerId());
            return new Access(self || channel.isPublic(), self, self, self);
        }
        TeamMember membership = viewerId == null ? null : memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, channel.getOwnerId())
                .eq(TeamMember::getUserId, viewerId).last("LIMIT 1"));
        boolean member = membership != null;
        String role = membership == null ? null : membership.getRole();
        boolean write = member && WRITE_ROLES.contains(role);
        return new Access(member || channel.isPublic(), write, TeamMember.ROLE_OWNER.equals(role), member);
    }

    private ChannelSummaryResponse toSummary(Channel channel) {
        return toSummary(channel, null);
    }

    private ChannelSummaryResponse toSummary(Channel channel, Long viewerId) {
        long documents = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channel.getId())
                .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
        boolean subscribed = viewerId != null && subscriptionMapper.selectCount(
                new LambdaQueryWrapper<ChannelSubscription>()
                        .eq(ChannelSubscription::getChannelId, channel.getId())
                        .eq(ChannelSubscription::getUserId, viewerId)) > 0;
        return new ChannelSummaryResponse(
                String.valueOf(channel.getId()), channel.getOwnerType(),
                String.valueOf(channel.getOwnerId()), ownerDisplayName(channel),
                channel.getName(), channel.getSlug(), channel.getDescription(),
                channel.isPublic(), Boolean.TRUE.equals(channel.getPinned()),
                channel.getPinnedOrder() == null ? 0 : channel.getPinnedOrder(), documents,
                channel.getReadmeKey(), channel.getAgentKey(), channel.getAgentName(),
                channel.getAgentDescription(), channel.getAgentTags(),
                siteSettingService.formatDateTime(channel.getUpdateTime()),
                channel.webSearchEnabled(), channel.featured(), subscribed, channel.archived());
    }

    private FolderResponse toFolder(Folder folder) {
        return new FolderResponse(String.valueOf(folder.getId()),
                folder.getParentId() == null ? null : String.valueOf(folder.getParentId()),
                folder.getName(), folder.getDepth() == null ? 1 : folder.getDepth());
    }

    private ChannelDocumentResponse toDocument(KbDocument document) {
        return new ChannelDocumentResponse(String.valueOf(document.getId()), document.getName(),
                document.getFolderId() == null ? null : String.valueOf(document.getFolderId()),
                document.getSize() == null ? 0 : document.getSize(),
                document.getContentType(), document.getStatus(), document.getErrorMessage(),
                document.getRetryCount() == null ? 0 : document.getRetryCount(),
                document.getTitle(), document.getDocumentType(),
                siteSettingService.formatDateTime(document.getExpirationTime()),
                siteSettingService.formatDateTime(document.getCreateTime()),
                siteSettingService.formatDateTime(document.getProcessedAt()));
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ex) {
            throw BusinessException.badRequest("无效的时效时间");
        }
    }

    private String ownerHandle(Channel channel) {
        if (Channel.OWNER_TEAM.equals(channel.getOwnerType())) {
            Team team = teamMapper.selectById(channel.getOwnerId());
            return team == null ? String.valueOf(channel.getOwnerId()) : team.getSlug();
        }
        User user = userMapper.selectById(channel.getOwnerId());
        return user == null ? String.valueOf(channel.getOwnerId()) : user.getUsername();
    }

    private String ownerDisplayName(Channel channel) {
        if (Channel.OWNER_TEAM.equals(channel.getOwnerType())) {
            Team team = teamMapper.selectById(channel.getOwnerId());
            return team == null ? String.valueOf(channel.getOwnerId()) : team.getSlug();
        }
        User user = userMapper.selectById(channel.getOwnerId());
        return user == null ? String.valueOf(channel.getOwnerId()) : user.getUsername();
    }

    private String folderPath(Folder folder) {
        List<String> segments = new ArrayList<>();
        Folder current = folder;
        int guard = 0;
        while (current != null && guard++ < 16) {
            segments.add(sanitizeSegment(current.getName()));
            current = current.getParentId() == null ? null : folderMapper.selectById(current.getParentId());
        }
        java.util.Collections.reverse(segments);
        return String.join("/", segments);
    }

    private String sanitizeSegment(String value) {
        String sanitized = value == null ? "" : value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return sanitized.isEmpty() ? "folder" : sanitized;
    }

    private String markdownKind(String filename) {
        String base = markdownBaseName(filename);
        if ("agent".equals(base)) {
            return "AGENT";
        }
        return "readme".equals(base) ? "README" : null;
    }

    private String markdownBaseName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String safeObjectName(String filename, String extension) {
        String name = filename == null ? "" : filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\x00-\\x1f\\x7f]", "").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "file" + (extension.isEmpty() ? "" : "." + extension);
        }
        if (name.length() > 180) {
            String suffix = extension.isEmpty() ? "" : "." + extension;
            name = name.substring(0, 180 - suffix.length()) + suffix;
        }
        return name;
    }

    private boolean isRootAssetsFolder(Folder folder) {
        return folder != null && folder.getParentId() == null
                && "assets".equalsIgnoreCase(folder.getName() == null ? "" : folder.getName().trim());
    }

    private String rewriteReadmeAssets(Channel channel, String content) {
        if (!StringUtils.hasText(content) || !content.toLowerCase(Locale.ROOT).contains("assets/")) {
            return content;
        }
        Folder assetsFolder = folderMapper.selectOne(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getChannelId, channel.getId())
                .isNull(Folder::getParentId)
                .apply("LOWER(name) = 'assets'")
                .last("LIMIT 1"));
        if (assetsFolder == null) {
            return content;
        }
        Map<String, String> keys = new HashMap<>();
        documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getChannelId, channel.getId())
                        .eq(KbDocument::getFolderId, assetsFolder.getId())
                        .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()))
                .forEach(doc -> {
                    if (StringUtils.hasText(doc.getName()) && StringUtils.hasText(doc.getObjectKey())) {
                        keys.putIfAbsent(doc.getName(), doc.getObjectKey());
                    }
                });
        if (keys.isEmpty()) {
            return content;
        }
        return rewriteAssetPattern(HTML_ASSET, MARKDOWN_ASSET, channel, keys, content);
    }

    private String rewriteAssetPattern(Pattern html, Pattern markdown, Channel channel,
                                       Map<String, String> keys, String content) {
        String rewritten = replaceAssetMatches(html, channel, keys, content, 2, 3);
        return replaceAssetMatches(markdown, channel, keys, rewritten, 3, 4);
    }

    private String replaceAssetMatches(Pattern pattern, Channel channel, Map<String, String> keys,
                                       String content, int pathGroup, int suffixGroup) {
        Matcher matcher = pattern.matcher(content);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String url = assetUrl(channel, keys, matcher.group(pathGroup));
            if (url == null) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(output, Matcher.quoteReplacement(
                        matcher.group(1) + url + matcher.group(suffixGroup)));
            }
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String assetUrl(Channel channel, Map<String, String> keys, String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        if (!path.startsWith("assets/")) {
            return null;
        }
        String name = path.substring("assets/".length());
        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep the literal path
        }
        String decodedName = name;
        String key = keys.get(decodedName);
        if (key == null) {
            key = keys.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(decodedName))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
        }
        if (key == null) {
            return null;
        }
        try {
            return storageService.presignedUrl(key, 3600);
        } catch (RuntimeException ex) {
            log.debug("Unable to sign README asset for channel {}: {}", channel.getId(), ex.getMessage());
            return null;
        }
    }

    private void deleteObjectQuietly(String key) {
        try {
            storageService.deleteObject(key);
        } catch (RuntimeException ex) {
            log.warn("Unable to delete replaced markdown object {}: {}", key, ex.getMessage());
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Access(boolean read, boolean write, boolean manage, boolean member) {
    }

    private Long teamIdOf(Channel channel) {
        return Channel.OWNER_TEAM.equals(channel.getOwnerType()) ? channel.getOwnerId() : null;
    }

    private String teamNameOf(Channel channel) {
        if (!Channel.OWNER_TEAM.equals(channel.getOwnerType())) {
            return null;
        }
        var team = teamMapper.selectById(channel.getOwnerId());
        return team == null ? null : team.getName();
    }
}
