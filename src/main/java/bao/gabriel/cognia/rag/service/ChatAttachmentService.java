package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.system.dto.chat.ChatAttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ChatAttachmentService {

    ChatAttachmentResponse prepare(MultipartFile file);
}
