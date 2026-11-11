package bao.gabriel.cognia.system.dto.favorite;

import java.util.List;

public record FavoriteListResponse(
        List<FavoriteConversationResponse> conversations,
        List<FavoriteMessageResponse> messages
) {
}
