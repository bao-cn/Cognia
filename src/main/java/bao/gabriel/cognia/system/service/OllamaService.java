package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.model.OllamaPullStatusResponse;
import bao.gabriel.cognia.system.dto.model.OllamaStatusResponse;

public interface OllamaService {
    OllamaStatusResponse status(Long providerId);
    OllamaPullStatusResponse pull(Long providerId, String model, boolean insecure);
    OllamaPullStatusResponse pullStatus(String taskId);
}
