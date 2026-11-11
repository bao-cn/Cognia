package bao.gabriel.cognia.system.dto.channel;

import java.util.List;

public record ChannelDetailResponse(
        ChannelSummaryResponse channel,
        List<FolderResponse> folders,
        List<ChannelDocumentResponse> documents,
        boolean canWrite,
        boolean canManage,
        boolean canNotify,
        boolean canViewInsights
) {
}
