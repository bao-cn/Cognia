package bao.gabriel.cognia.system.dto.chat;

import java.util.List;

public record ConversationSummaryResponse(
        String id,
        String title,
        Long modelId,
        List<Long> channelIds,
        String createTime,
        String updateTime
) {
}
