package bao.gabriel.cognia.system.dto.chat;

import java.util.List;

public record ConversationDetailResponse(
        String id,
        String title,
        Long modelId,
        List<Long> channelIds,
        List<ConversationMessageResponse> messages,
        String createTime,
        String updateTime
) {
}
