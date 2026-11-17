package bao.gabriel.cognia.system.dto.feedback;

import java.util.List;
import java.util.Map;

public record FeedbackDetailResponse(
        FeedbackResponse feedback,
        List<Map<String, Object>> messages,
        String traceJson
) {}
