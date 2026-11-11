package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.channel.ChannelDetailResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelDocumentResponse;
import bao.gabriel.cognia.system.dto.channel.ChannelSummaryResponse;
import bao.gabriel.cognia.system.dto.channel.CreateChannelRequest;
import bao.gabriel.cognia.system.dto.channel.CreateFolderRequest;
import bao.gabriel.cognia.system.dto.channel.FolderResponse;
import bao.gabriel.cognia.system.dto.channel.UpdateAgentMetaRequest;
import bao.gabriel.cognia.system.dto.channel.UpdateChannelRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ChannelService {

    record DocumentContent(String fileName, String contentType, byte[] bytes) {
    }

    List<ChannelSummaryResponse> list(String ownerType, Long ownerId, Long viewerId);

    ChannelDetailResponse detail(Long channelId, Long viewerId);

    ChannelSummaryResponse create(Long userId, CreateChannelRequest request);

    ChannelSummaryResponse update(Long userId, Long channelId, UpdateChannelRequest request);

    void delete(Long userId, Long channelId);

    ChannelSummaryResponse setPinned(Long userId, Long channelId, boolean pinned);

    FolderResponse createFolder(Long userId, Long channelId, CreateFolderRequest request);

    void deleteFolder(Long userId, Long channelId, Long folderId);

    ChannelDocumentResponse uploadDocument(Long userId, Long channelId, Long folderId, MultipartFile file,
                                          String title, String documentType, String expirationTime);

    ChannelDocumentResponse uploadMarkdown(Long userId, Long channelId, String kind, MultipartFile file);

    String readMarkdown(Long channelId, Long viewerId, String kind);

    List<ChannelDocumentResponse> documents(Long channelId, Long viewerId, Long folderId);

    void deleteDocument(Long userId, Long channelId, Long documentId);

    /**
     * Raw document bytes for the in-app citation preview. Access follows the
     * same read rules as the channel itself.
     */
    DocumentContent documentContent(Long documentId, Long viewerId);

    ChannelSummaryResponse updateAgentMeta(Long userId, Long channelId, UpdateAgentMetaRequest request);

    void retryDocument(Long userId, Long channelId, Long documentId);

    /**
     * Whether the channel allows chat requests to use web search tools.
     */
    boolean webSearchEnabled(Long channelId);

    /**
     * AGENT.md knowledge for the given channels, restricted to channels visible
     * to the viewer. Returns an empty string when nothing is available.
     */
    String agentPrompt(List<Long> channelIds, Long viewerId);

    /**
     * Write access mirrors document upload rights: personal owner or team
     * OWNER/MANAGER/EDITOR/MAINTAINER.
     */
    boolean hasWriteAccess(Long channelId, Long userId);

    boolean hasNotificationAccess(Long channelId, Long userId);

    ChannelSummaryResponse setWebSearchEnabled(Long userId, Long channelId, boolean enabled);
}
