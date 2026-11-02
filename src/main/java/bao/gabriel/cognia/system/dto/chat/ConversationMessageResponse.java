package bao.gabriel.cognia.system.dto.chat;

public record ConversationMessageResponse(
        String role,
        String content,
        String reasoning,
        String createTime
) {
}
