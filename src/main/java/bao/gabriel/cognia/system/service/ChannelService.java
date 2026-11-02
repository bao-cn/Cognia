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

    ChannelSummaryResponse updateAgentMeta(Long userId, Long channelId, UpdateAgentMetaRequest request);

    void retryDocument(Long userId, Long channelId, Long documentId);
}
