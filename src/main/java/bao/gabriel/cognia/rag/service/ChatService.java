package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.system.dto.chat.ChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    SseEmitter stream(ChatRequest request, Long userId);
}
