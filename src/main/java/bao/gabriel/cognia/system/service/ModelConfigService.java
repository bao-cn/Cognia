package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.model.ModelConfigResponse;
import bao.gabriel.cognia.system.dto.model.ModelProviderResponse;
import bao.gabriel.cognia.system.dto.model.PublicModelResponse;
import bao.gabriel.cognia.system.dto.model.RemoteModelResponse;
import bao.gabriel.cognia.system.dto.model.SaveModelConfigRequest;
import bao.gabriel.cognia.system.dto.model.SaveModelProviderRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface ModelConfigService {

    List<ModelProviderResponse> listProviders();

    ModelProviderResponse createProvider(SaveModelProviderRequest request, Long operatorId);

    ModelProviderResponse updateProvider(Long providerId, SaveModelProviderRequest request, Long operatorId);

    void deleteProvider(Long providerId);

    List<RemoteModelResponse> fetchRemoteModels(Long providerId);

    List<RemoteModelResponse> fetchRemoteModels(String baseUrl, String apiKey, String protocol);

    List<ModelConfigResponse> listModels(Long providerId);

    ModelConfigResponse saveModel(SaveModelConfigRequest request);

    void deleteModel(Long modelId);

    List<PublicModelResponse> publicModels();

    ResolvedModel resolveChat(Long requestedModelId);

    ResolvedModel resolveModel(Long modelId);

    ObjectNode extraBody(ResolvedModel model, String effortOverride);

    record ResolvedModel(
            Long id,
            Long providerId,
            String providerName,
            String vendor,
            String baseUrl,
            String apiKey,
            String protocol,
            String modelId,
            String modelType,
            boolean supportsTools,
            boolean supportsText,
            boolean thinkingEnabled,
            boolean supportsVision,
            String reasoningEffort,
            Map<String, String> reasoningLevels,
            String extraBody,
            Integer dimension,
            Integer contextWindow,
            Integer maxOutputTokens
    ) {
        /**
         * Effective context window used for token budgeting; never zero.
         */
        public int contextWindowOrDefault() {
            return contextWindow == null || contextWindow < 1000 ? 32000 : contextWindow;
        }

        public int maxOutputTokensOrDefault() {
            return maxOutputTokens == null || maxOutputTokens < 1 ? 4096 : maxOutputTokens;
        }

        /**
         * Maps a UI reasoning label ("low", "high", ...) to the value the
         * provider expects. Falls back to the label itself when the model has
         * no custom mapping.
         */
        public String reasoningValue(String label) {
            if (label == null || label.isBlank()) {
                return null;
            }
            String key = label.trim().toLowerCase(java.util.Locale.ROOT);
            if (reasoningLevels != null && reasoningLevels.containsKey(key)) {
                return reasoningLevels.get(key);
            }
            return key;
        }

        public static Map<String, String> parseReasoningLevels(String json, com.fasterxml.jackson.databind.ObjectMapper mapper) {
            Map<String, String> levels = new LinkedHashMap<>();
            if (json == null || json.isBlank()) {
                return levels;
            }
            try {
                var node = mapper.readTree(json);
                if (node.isArray()) {
                    for (var item : node) {
                        String label = item.path("label").asText(null);
                        String value = item.path("value").asText(null);
                        if (label != null && !label.isBlank()) {
                            levels.put(label.trim().toLowerCase(java.util.Locale.ROOT),
                                    value == null || value.isBlank() ? label.trim().toLowerCase(java.util.Locale.ROOT) : value);
                        }
                    }
                }
            } catch (Exception ignored) {
                // invalid presets fall back to provider defaults
            }
            return levels;
        }
    }
}
