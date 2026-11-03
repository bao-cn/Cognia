package bao.gabriel.cognia.system.dto.channel;

public record ChannelNotificationResponse(
        Long id,
        Long channelId,
        String title,
        String content,
        String createTime
) {
}
