package bao.gabriel.cognia.system.dto.feedback;

public record FeedbackResponse(
        Long id,
        Long messageId,
        Long conversationId,
        Long userId,
        String username,
        String nickname,
        String feedbackType,
        String reason,
        String problemType,
        String status,
        String note,
        String traceId,
        String question,
        String answer,
        String createTime,
        String updateTime
) {}
