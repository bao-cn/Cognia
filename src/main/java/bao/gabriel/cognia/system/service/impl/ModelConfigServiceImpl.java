package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.model.ModelConfigResponse;
import bao.gabriel.cognia.system.dto.model.ModelProviderResponse;
import bao.gabriel.cognia.system.dto.model.PublicModelResponse;
import bao.gabriel.cognia.system.dto.model.RemoteModelResponse;
import bao.gabriel.cognia.system.dto.model.SaveModelConfigRequest;
import bao.gabriel.cognia.system.dto.model.SaveModelProviderRequest;
import bao.gabriel.cognia.system.entity.ModelConfig;
import bao.gabriel.cognia.system.entity.ModelProvider;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.mapper.ModelConfigMapper;
import bao.gabriel.cognia.system.mapper.ModelProviderMapper;
import bao.gabriel.cognia.system.mapper.RagSettingMapper;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelProviderMapper providerMapper;
    private final ModelConfigMapper modelMapper;
    private final RagSettingMapper ragSettingMapper;
    private final SecurityCryptoService cryptoService;
    private final SiteSettingService siteSettingService;
    private final OpenAiCompatibleClient client;

    @Override
    public List<ModelProviderResponse> listProviders() {
        List<ModelProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<ModelProvider>()
                .orderByAsc(ModelProvider::getId));
        List<ModelProviderResponse> result = new ArrayList<>();
        for (ModelProvider provider : providers) {
            Long count = modelMapper.selectCount(new LambdaQueryWrapper<ModelConfig>()
                    .eq(ModelConfig::getProviderId, provider.getId()));
            result.add(toProvider(provider, count == null ? 0 : count));
        }
        return result;
    }

    @Override
    @Transactional
    public ModelProviderResponse createProvider(SaveModelProviderRequest request, Long operatorId) {
        if (!StringUtils.hasText(request.name()) || !StringUtils.hasText(request.baseUrl())) {
            throw BusinessException.badRequest("供应商名称和 API 端点不能为空");
        }
        ModelProvider provider = new ModelProvider();
        provider.setName(request.name().trim());
        provider.setVendor(normalizeVendor(request.vendor()));
        provider.setBaseUrl(client.normalizeBaseUrl(request.baseUrl()));
        provider.setApiKey(cryptoService.encrypt(trimToNull(request.apiKey())));
        provider.setEnabled(request.enabled() == null || request.enabled());
        provider.setUpdatedBy(operatorId);
        provider.setCreateTime(LocalDateTime.now());
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.insert(provider);
        return toProvider(provider, 0);
    }

    @Override
    @Transactional
    public ModelProviderResponse updateProvider(Long providerId, SaveModelProviderRequest request, Long operatorId) {
        ModelProvider provider = requireProvider(providerId);
        if (request.name() != null) {
            if (!StringUtils.hasText(request.name())) {
                throw BusinessException.badRequest("供应商名称不能为空");
            }
            provider.setName(request.name().trim());
        }
        if (request.vendor() != null) {
            provider.setVendor(normalizeVendor(request.vendor()));
        }
        if (request.baseUrl() != null) {
            provider.setBaseUrl(client.normalizeBaseUrl(request.baseUrl()));
        }
        if (StringUtils.hasText(request.apiKey())) {
            provider.setApiKey(cryptoService.encrypt(request.apiKey().trim()));
        }
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            provider.setApiKey(null);
        }
        if (request.enabled() != null) {
            provider.setEnabled(request.enabled());
        }
        provider.setUpdatedBy(operatorId);
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(provider);
        Long count = modelMapper.selectCount(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getProviderId, providerId));
        return toProvider(provider, count == null ? 0 : count);
    }

    @Override
    @Transactional
    public void deleteProvider(Long providerId) {
        ModelProvider provider = requireProvider(providerId);
        RagSetting rag = ragSettingMapper.selectById(RagSetting.SINGLETON_ID);
        List<Long> ids = modelMapper.selectList(new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getProviderId, providerId))
                .stream().map(ModelConfig::getId).toList();
        if (rag != null && ids.stream().anyMatch(id -> Objects.equals(id, rag.getChatModelId())
                || Objects.equals(id, rag.getEmbeddingModelId())
                || Objects.equals(id, rag.getRerankModelId()))) {
            throw BusinessException.conflict("该供应商下仍有模型被检索/向量配置使用，请先调整配置");
        }
        modelMapper.delete(new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getProviderId, providerId));
        providerMapper.deleteById(provider.getId());
    }

    @Override
    public List<RemoteModelResponse> fetchRemoteModels(Long providerId) {
        ModelProvider provider = requireProvider(providerId);
        return fetchRemoteModels(provider.getBaseUrl(), cryptoService.decrypt(provider.getApiKey()));
    }

    @Override
    public List<RemoteModelResponse> fetchRemoteModels(String baseUrl, String apiKey) {
        String normalized = client.normalizeBaseUrl(baseUrl);
        JsonNode response = client.getJson(normalized, "/models", apiKey);
        JsonNode data = response.path("data");
        List<RemoteModelResponse> result = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (StringUtils.hasText(id)) {
                    result.add(new RemoteModelResponse(id, item.path("owned_by").asText(null)));
                }
            }
        }
        result.sort(Comparator.comparing(RemoteModelResponse::id, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Override
    public List<ModelConfigResponse> listModels(Long providerId) {
        LambdaQueryWrapper<ModelConfig> query = new LambdaQueryWrapper<ModelConfig>()
                .orderByAsc(ModelConfig::getProviderId)
                .orderByAsc(ModelConfig::getModelType)
                .orderByAsc(ModelConfig::getModelId);
        if (providerId != null) {
            query.eq(ModelConfig::getProviderId, providerId);
        }
        List<ModelConfig> models = modelMapper.selectList(query);
        List<ModelConfigResponse> result = new ArrayList<>();
        for (ModelConfig model : models) {
            result.add(toModel(model, providerMapper.selectById(model.getProviderId())));
        }
        return result;
    }

    @Override
    @Transactional
    public ModelConfigResponse saveModel(SaveModelConfigRequest request) {
        ModelProvider provider = requireProvider(request.providerId());
        String modelId = request.modelId().trim();
        String type = normalizeType(request.modelType());
        ModelConfig model = modelMapper.selectOne(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getProviderId, provider.getId())
                .eq(ModelConfig::getModelId, modelId)
                .eq(ModelConfig::getModelType, type));
        boolean created = model == null;
        if (created) {
            model = new ModelConfig();
            model.setProviderId(provider.getId());
            model.setModelId(modelId);
            model.setModelType(type);
            model.setEnabled(true);
            model.setVisible(true);
            model.setSupportsTools(false);
            model.setThinkingEnabled(false);
            model.setSupportsVision(false);
            model.setContextWindow(ModelConfig.DEFAULT_CONTEXT_WINDOW);
            model.setCreateTime(LocalDateTime.now());
        }
        if (request.displayName() != null) {
            model.setDisplayName(trimToNull(request.displayName()));
        }
        if (model.getDisplayName() == null) {
            model.setDisplayName(modelId);
        }
        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        if (request.visible() != null) {
            model.setVisible(request.visible());
        }
        if (request.supportsTools() != null) {
            model.setSupportsTools(request.supportsTools());
        }
        if (request.thinkingEnabled() != null) {
            model.setThinkingEnabled(request.thinkingEnabled());
        }
        if (request.supportsVision() != null) {
            model.setSupportsVision(request.supportsVision());
        }
        if (request.reasoningEffort() != null) {
            model.setReasoningEffort(normalizeEffort(request.reasoningEffort()));
        }
        if (request.extraBody() != null) {
            String extra = trimToNull(request.extraBody());
            if (extra != null) {
                try {
                    ObjectMapper mapper = client.mapper();
                    mapper.readTree(extra);
                } catch (Exception ex) {
                    throw BusinessException.badRequest("自定义参数必须是合法 JSON");
                }
            }
            model.setExtraBody(extra);
        }
        if (request.dimension() != null) {
            model.setDimension(request.dimension());
        }
        if (request.contextWindow() != null) {
            model.setContextWindow(request.contextWindow());
        }
        if (model.getContextWindow() == null) {
            model.setContextWindow(ModelConfig.DEFAULT_CONTEXT_WINDOW);
        }
        model.setUpdateTime(LocalDateTime.now());
        if (created) {
            modelMapper.insert(model);
        } else {
            modelMapper.updateById(model);
        }
        return toModel(model, provider);
    }

    @Override
    public void deleteModel(Long modelId) {
        ModelConfig model = requireModel(modelId);
        RagSetting rag = ragSettingMapper.selectById(RagSetting.SINGLETON_ID);
        if (rag != null && (Objects.equals(modelId, rag.getChatModelId())
                || Objects.equals(modelId, rag.getEmbeddingModelId())
                || Objects.equals(modelId, rag.getRerankModelId()))) {
            throw BusinessException.conflict("该模型正在被检索/向量配置使用，请先调整配置");
        }
        modelMapper.deleteById(model.getId());
    }

    @Override
    public List<PublicModelResponse> publicModels() {
        List<ModelConfig> models = modelMapper.selectList(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getModelType, ModelConfig.TYPE_CHAT)
                .eq(ModelConfig::getEnabled, true)
                .eq(ModelConfig::getVisible, true)
                .orderByAsc(ModelConfig::getProviderId)
                .orderByAsc(ModelConfig::getDisplayName));
        List<PublicModelResponse> result = new ArrayList<>();
        for (ModelConfig model : models) {
            ModelProvider provider = providerMapper.selectById(model.getProviderId());
            if (provider == null || !provider.enabled()) {
                continue;
            }
            result.add(new PublicModelResponse(model.getId(), provider.getId(), provider.getName(),
                    model.getModelId(), displayName(model), model.getModelType(),
                    model.supportsTools(), model.thinkingEnabled(), model.supportsVision()));
        }
        return result;
    }

    @Override
    public ResolvedModel resolveChat(Long requestedModelId) {
        if (requestedModelId != null) {
            ModelConfig model = modelMapper.selectById(requestedModelId);
            if (model != null && ModelConfig.TYPE_CHAT.equals(model.getModelType())
                    && model.enabled() && model.visible()) {
                return resolveRequired(model);
            }
        }
        RagSetting rag = ragSettingMapper.selectById(RagSetting.SINGLETON_ID);
        if (rag != null && rag.getChatModelId() != null) {
            ModelConfig model = modelMapper.selectById(rag.getChatModelId());
            if (model != null && model.enabled()) {
                return resolveRequired(model);
            }
        }
        List<ModelConfig> models = modelMapper.selectList(new LambdaQueryWrapper<ModelConfig>()
                .eq(ModelConfig::getModelType, ModelConfig.TYPE_CHAT)
                .eq(ModelConfig::getEnabled, true)
                .eq(ModelConfig::getVisible, true)
                .orderByAsc(ModelConfig::getId)
                .last("LIMIT 1"));
        if (models.isEmpty()) {
            throw BusinessException.badRequest("管理员尚未开放任何聊天模型");
        }
        return resolveRequired(models.get(0));
    }

    @Override
    public ResolvedModel resolveModel(Long modelId) {
        ModelConfig model = requireModel(modelId);
        return resolveRequired(model);
    }

    @Override
    public ObjectNode extraBody(ResolvedModel model, String effortOverride) {
        ObjectNode body = client.mapper().createObjectNode();
        String effort = normalizeEffort(effortOverride != null ? effortOverride : model.reasoningEffort());
        if ("DEEPSEEK".equals(model.vendor())) {
            ObjectNode thinking = body.putObject("thinking");
            thinking.put("type", model.thinkingEnabled() ? "enabled" : "disabled");
            if (model.thinkingEnabled() && effort != null) {
                body.put("reasoning_effort", effort);
            }
        } else if ("QWEN".equals(model.vendor())) {
            body.put("enable_thinking", model.thinkingEnabled());
            if (model.thinkingEnabled() && effort != null) {
                body.put("reasoning_effort", effort);
            }
        } else if (effort != null) {
            body.put("reasoning_effort", effort);
        }
        if (StringUtils.hasText(model.extraBody())) {
            try {
                JsonNode custom = client.mapper().readTree(model.extraBody());
                if (custom.isObject()) {
                    custom.fields().forEachRemaining(entry -> body.set(entry.getKey(), entry.getValue()));
                }
            } catch (Exception ex) {
                log.warn("Ignoring invalid extra_body for model {}: {}", model.modelId(), ex.getMessage());
            }
        }
        return body;
    }

    private ResolvedModel resolveRequired(ModelConfig model) {
        ModelProvider provider = requireProvider(model.getProviderId());
        return new ResolvedModel(model.getId(), provider.getId(), provider.getName(), provider.getVendor(),
                provider.getBaseUrl(), cryptoService.decrypt(provider.getApiKey()), model.getModelId(),
                model.getModelType(), model.supportsTools(), model.thinkingEnabled(), model.supportsVision(),
                model.getReasoningEffort(), model.getExtraBody(), model.getDimension(),
                model.contextWindow());
    }

    private ModelProvider requireProvider(Long id) {
        ModelProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw BusinessException.notFound("模型供应商不存在");
        }
        return provider;
    }

    private ModelConfig requireModel(Long id) {
        ModelConfig model = modelMapper.selectById(id);
        if (model == null) {
            throw BusinessException.notFound("模型配置不存在");
        }
        return model;
    }

    private ModelProviderResponse toProvider(ModelProvider provider, long count) {
        return new ModelProviderResponse(provider.getId(), provider.getName(), provider.getVendor(),
                provider.getBaseUrl(), StringUtils.hasText(provider.getApiKey()), provider.enabled(), count,
                siteSettingService.formatDateTime(provider.getCreateTime()),
                siteSettingService.formatDateTime(provider.getUpdateTime()));
    }

    private ModelConfigResponse toModel(ModelConfig model, ModelProvider provider) {
        return new ModelConfigResponse(model.getId(), model.getProviderId(),
                provider == null ? "" : provider.getName(), model.getModelId(), displayName(model),
                model.getModelType(), model.enabled(), model.visible(), model.supportsTools(),
                model.thinkingEnabled(), model.supportsVision(), model.getReasoningEffort(), model.getExtraBody(),
                model.getDimension(), model.getContextWindow(),
                siteSettingService.formatDateTime(model.getUpdateTime()));
    }

    private String displayName(ModelConfig model) {
        return StringUtils.hasText(model.getDisplayName()) ? model.getDisplayName() : model.getModelId();
    }

    private String normalizeVendor(String vendor) {
        String value = vendor == null ? "OPENAI" : vendor.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "DEEPSEEK", "QWEN", "CUSTOM", "OPENAI" -> value;
            default -> "OPENAI";
        };
    }

    private String normalizeType(String type) {
        String value = type == null ? ModelConfig.TYPE_CHAT : type.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case ModelConfig.TYPE_CHAT, ModelConfig.TYPE_EMBEDDING, ModelConfig.TYPE_RERANK -> value;
            default -> throw BusinessException.badRequest("未知模型类型");
        };
    }

    private String normalizeEffort(String effort) {
        if (!StringUtils.hasText(effort)) {
            return null;
        }
        String value = effort.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "low", "medium", "high", "max", "minimal" -> value;
            default -> throw BusinessException.badRequest("推理强度只能是 low/medium/high/max");
        };
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
