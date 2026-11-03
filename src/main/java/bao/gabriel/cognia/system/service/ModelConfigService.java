package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.model.ModelConfigResponse;
import bao.gabriel.cognia.system.dto.model.ModelProviderResponse;
import bao.gabriel.cognia.system.dto.model.PublicModelResponse;
import bao.gabriel.cognia.system.dto.model.RemoteModelResponse;
import bao.gabriel.cognia.system.dto.model.SaveModelConfigRequest;
import bao.gabriel.cognia.system.dto.model.SaveModelProviderRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface ModelConfigService {

    List<ModelProviderResponse> listProviders();

    ModelProviderResponse createProvider(SaveModelProviderRequest request, Long operatorId);

    ModelProviderResponse updateProvider(Long providerId, SaveModelProviderRequest request, Long operatorId);

    void deleteProvider(Long providerId);

    List<RemoteModelResponse> fetchRemoteModels(Long providerId);

    List<RemoteModelResponse> fetchRemoteModels(String baseUrl, String apiKey);

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
            String modelId,
            String modelType,
            boolean supportsTools,
            boolean thinkingEnabled,
            boolean supportsVision,
            String reasoningEffort,
            String extraBody,
            Integer dimension,
            Integer contextWindow
    ) {
        /**
         * Effective context window used for token budgeting; never zero.
         */
        public int contextWindowOrDefault() {
            return contextWindow == null || contextWindow < 1000 ? 32000 : contextWindow;
        }
    }
}
