package bao.gabriel.cognia.system.dto.favorite;

public record FavoriteConversationResponse(
        String id,
        String conversationId,
        String title,
        String createTime
) {
}
