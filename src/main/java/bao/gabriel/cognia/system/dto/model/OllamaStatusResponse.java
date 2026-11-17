package bao.gabriel.cognia.system.dto.model;

import java.util.List;

public record OllamaStatusResponse(
        boolean configured,
        boolean connected,
        Long providerId,
        String providerName,
        String baseUrl,
        List<String> models,
        String message
) {
}
