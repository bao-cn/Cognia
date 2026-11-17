package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.feedback.FeedbackDetailResponse;
import bao.gabriel.cognia.system.dto.feedback.FeedbackResponse;

public interface FeedbackService {
    FeedbackResponse submit(Long userId, Long messageId, String type, String reason);
    PageResult<FeedbackResponse> page(long page, long size, String type, String status);
    FeedbackDetailResponse detail(Long id);
    FeedbackResponse update(Long id, String problemType, String status, String note);
}
