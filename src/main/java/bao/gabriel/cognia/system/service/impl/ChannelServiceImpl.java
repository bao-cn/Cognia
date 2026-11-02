package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.storage.UploadProgressListener;
import bao.gabriel.cognia.common.util.ReservedNames;
import bao.gabriel.cognia.system.dto.channel.ChannelDetailResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelDocumentResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelSummaryResponse;
import bao.gabriel.cognia.system.dto.channel.CreateChannelRequest;
import bao.gabriel.cognia.system.dto.channel.CreateFolderRequest;
import bao.gabriel.cognia.system.dto.channel.FolderResponse;
import bao.gabriel.cognia.system.dto.channel.UpdateAgentMetaRequest;
import bao.gabriel.cognia.system.dto.channel.UpdateChannelRequest;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.Folder;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.TeamMember;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.FolderMapper;
import bao.gabriel.cognia.system.mapper.KbDocumentMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.TeamMemberMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.DocumentIngestionService;
import bao.gabriel.cognia.system.service.DocumentStateMachine;
import bao.gabriel.cognia.system.service.FileDeletionService;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.dto.storage.StoredObjectResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private static final Pattern OWNER_TYPE = Pattern.compile("^(USER|TEAM)$");
    private static final Set<String> WRITE_ROLES = Set.of(
            TeamMember.ROLE_OWNER, TeamMember.ROLE_MANAGER, TeamMember.ROLE_EDITOR,
            "MAINTAINER");

    private final ChannelMapper channelMapper;
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
                .stream().map(this::toSummary).toList();
    }

    @Override
    public ChannelDetailResponse detail(Long channelId, Long viewerId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) {
            throw BusinessException.notFound("频道不存在");
        }
        List<FolderResponse> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                        .eq(Folder::getChannelId, channelId)
                        .orderByAsc(Folder::getDepth).orderByAsc(Folder::getId))
                .stream().map(this::toFolder).toList();
        List<ChannelDocumentResponse> documents = documents(channelId, viewerId, null);
        return new ChannelDetailResponse(toSummary(channel), folders, documents,
                access.write(), access.manage());
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
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        channelMapper.insert(channel);
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
        if (request.isPublic() != null) {
            channel.setIsPublic(request.isPublic());
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
                                                  String title, String documentType, String expirationTime) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有上传文档的权限");
        }
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
        String base = "kb/" + ownerHandle(channel) + "/" + channel.getSlug();
        if (folder != null) {
            base += "/" + folderPath(folder);
        }
        String extension = extensionOf(file.getOriginalFilename());
        String key = base + "/" + UUID.randomUUID().toString().replace("-", "")
                + (extension.isEmpty() ? "" : "." + extension);
        StoredObjectResponse stored = storageService.uploadAtKey(file, key, UploadProgressListener.NOOP);
        LocalDateTime now = LocalDateTime.now();
        KbDocument document = new KbDocument();
        document.setOwnerType(channel.getOwnerType());
        document.setOwnerId(channel.getOwnerId());
        document.setChannelId(channelId);
        document.setFolderId(folderId);
        String originalName = file.getOriginalFilename() == null ? "未命名文档" : file.getOriginalFilename();
        document.setName(originalName);
        document.setTitle(StringUtils.hasText(title) ? title.trim() : originalName);
        document.setDocumentType(StringUtils.hasText(documentType) ? documentType.trim() : extension);
        document.setAuthor(ownerHandle(channel));
        document.setExpirationTime(parseDateTime(expirationTime));
        document.setVersion(1);
        document.setObjectKey(stored.key());
        document.setOriginalKey(stored.key());
        document.setSize(stored.size());
        document.setContentType(stored.contentType());
        document.setStatus(DocumentStateMachine.DocumentStatus.PENDING.name());
        document.setRetryCount(0);
        document.setCreatedBy(userId);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        documentMapper.insert(document);
        ingestionService.ingest(document.getId());
        KbDocument saved = documentMapper.selectById(document.getId());
        return toDocument(saved == null ? document : saved);
    }

    @Override
    @Transactional
    public ChannelDocumentResponse uploadMarkdown(Long userId, Long channelId, String kind, MultipartFile file) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有修改频道文档的权限");
        }
        String normalized = "AGENT".equalsIgnoreCase(kind) ? "AGENT" : "README";
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择 Markdown 文件");
        }
        String key = "kb/" + ownerHandle(channel) + "/" + channel.getSlug() + "/" + normalized + ".md";
        storageService.uploadAtKey(file, key, UploadProgressListener.NOOP);
        if ("AGENT".equals(normalized)) {
            channel.setAgentKey(key);
            applyAgentFrontmatter(channel, file);
        } else {
            channel.setReadmeKey(key);
        }
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return toDocumentFromMarkdown(normalized, key, file);
    }

    @Override
    public String readMarkdown(Long channelId, Long viewerId, String kind) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) {
            throw BusinessException.notFound("频道不存在");
        }
        boolean agent = "AGENT".equalsIgnoreCase(kind);
        String key = agent ? channel.getAgentKey() : channel.getReadmeKey();
        if (!StringUtils.hasText(key)) {
            return "";
        }
        try {
            return new String(storageService.readObject(key), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            log.warn("Unable to read channel markdown {} for channel {}: {}", kind, channelId, ex.getMessage());
            return "";
        }
    }

    @Override
    public List<ChannelDocumentResponse> documents(Long channelId, Long viewerId, Long folderId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, viewerId);
        if (!access.read()) {
            throw BusinessException.notFound("频道不存在");
        }
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getChannelId, channelId)
                        .eq(folderId != null, KbDocument::getFolderId, folderId)
                        .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name())
                        .orderByDesc(KbDocument::getCreateTime))
                .stream().map(this::toDocument).toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long userId, Long channelId, Long documentId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有删除文档的权限");
        }
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || !channelId.equals(document.getChannelId())) {
            throw BusinessException.notFound("文档不存在");
        }
        deletionService.logicalDelete(document, userId);
    }

    @Override
    @Transactional
    public ChannelSummaryResponse updateAgentMeta(Long userId, Long channelId, UpdateAgentMetaRequest request) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有修改 AGENT.md 的权限");
        }
        channel.setAgentName(trimToNull(request.name()));
        channel.setAgentDescription(trimToNull(request.description()));
        channel.setAgentTags(trimToNull(request.tags()));
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        if (channel.getAgentKey() != null) {
            rewriteAgentMarkdown(channel);
        }
        return toSummary(channel);
    }

    @Override
    public void retryDocument(Long userId, Long channelId, Long documentId) {
        Channel channel = requireChannel(channelId);
        Access access = access(channel, userId);
        if (!access.write()) {
            throw BusinessException.forbidden("你没有重试文档的权限");
        }
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null || !channelId.equals(document.getChannelId())) {
            throw BusinessException.notFound("文档不存在");
        }
        stateMachine.retry(document);
        documentMapper.updateById(document);
        ingestionService.ingest(document.getId());
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
        long documents = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getChannelId, channel.getId())
                .ne(KbDocument::getStatus, DocumentStateMachine.DocumentStatus.DELETED.name()));
        return new ChannelSummaryResponse(
                String.valueOf(channel.getId()), channel.getOwnerType(),
                String.valueOf(channel.getOwnerId()), ownerDisplayName(channel),
                channel.getName(), channel.getSlug(), channel.getDescription(),
                channel.isPublic(), Boolean.TRUE.equals(channel.getPinned()),
                channel.getPinnedOrder() == null ? 0 : channel.getPinnedOrder(), documents,
                channel.getReadmeKey(), channel.getAgentKey(), channel.getAgentName(),
                channel.getAgentDescription(), channel.getAgentTags(),
                siteSettingService.formatDateTime(channel.getUpdateTime()));
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

    private ChannelDocumentResponse toDocumentFromMarkdown(String kind, String key, MultipartFile file) {
        return new ChannelDocumentResponse(kind, kind + ".md", null, file.getSize(),
                "text/markdown", "READY", null, 0, kind, "markdown", null,
                siteSettingService.formatDateTime(LocalDateTime.now()), null);
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

    private void applyAgentFrontmatter(Channel channel, MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (!text.startsWith("---")) {
                return;
            }
            int end = text.indexOf("\n---", 3);
            if (end < 0) {
                return;
            }
            String frontmatter = text.substring(3, end);
            for (String line : frontmatter.split("\\R")) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim().replaceAll("^['\"]|['\"]$", "");
                switch (key) {
                    case "name" -> channel.setAgentName(value);
                    case "description" -> channel.setAgentDescription(value);
                    case "tags" -> channel.setAgentTags(value);
                    default -> {
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Unable to parse AGENT.md frontmatter", ex);
        }
    }

    private void rewriteAgentMarkdown(Channel channel) {
        String frontmatter = """
                ---
                name: %s
                description: %s
                tags: %s
                ---

                # %s

                %s
                """.formatted(nullToEmpty(channel.getAgentName()), nullToEmpty(channel.getAgentDescription()),
                nullToEmpty(channel.getAgentTags()), nullToEmpty(channel.getName()),
                nullToEmpty(channel.getAgentDescription()));
        // Persisting the canonical AGENT.md is best-effort; metadata is already saved.
        try {
            byte[] bytes = frontmatter.getBytes(StandardCharsets.UTF_8);
            storageService.uploadBytesAtKey(bytes, channel.getAgentKey(), "text/markdown");
        } catch (Exception ex) {
            log.warn("Unable to rewrite AGENT.md for channel {}: {}", channel.getId(), ex.getMessage());
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Access(boolean read, boolean write, boolean manage, boolean member) {
    }
}
