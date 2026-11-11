package bao.gabriel.cognia.system.dto.favorite;

public record FavoriteMessageResponse(
        String id,
        String messageId,
        String conversationId,
        String conversationTitle,
        String role,
        String content,
        String createTime
) {
}
