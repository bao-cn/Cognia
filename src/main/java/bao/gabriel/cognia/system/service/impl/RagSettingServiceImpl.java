package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.rag.RagSettingResponse;
import bao.gabriel.cognia.system.dto.rag.UpdateRagSettingRequest;
import bao.gabriel.cognia.system.entity.ModelConfig;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.mapper.ModelConfigMapper;
import bao.gabriel.cognia.system.mapper.RagSettingMapper;
import bao.gabriel.cognia.system.service.RagSettingService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RagSettingServiceImpl implements RagSettingService {

    private final RagSettingMapper mapper;
    private final ModelConfigMapper modelConfigMapper;
    private final SecurityCryptoService cryptoService;
    private final SiteSettingService siteSettingService;

    @Override
    public RagSetting getSetting() {
        RagSetting setting = mapper.selectById(RagSetting.SINGLETON_ID);
        if (setting == null) {
            setting = new RagSetting();
            setting.setId(RagSetting.SINGLETON_ID);
            setting.setChunkSize(1200);
            setting.setChunkOverlap(120);
            setting.setRetrievalTopK(20);
            setting.setRetrievalTopN(5);
            setting.setRerankEnabled(false);
            setting.setUpdateTime(LocalDateTime.now());
            mapper.insert(setting);
        }
        return setting;
    }

    @Override
    public RagSettingResponse getAdminSettings() {
        return toResponse(getSetting());
    }

    @Override
    @Transactional
    public RagSettingResponse updateSettings(UpdateRagSettingRequest request, Long operatorId) {
        RagSetting setting = getSetting();
        if (request.chatModelId() != null) {
            assertModelType(request.chatModelId(), ModelConfig.TYPE_CHAT, "聊天模型");
            setting.setChatModelId(request.chatModelId());
        }
        if (request.embeddingModelId() != null) {
            assertModelType(request.embeddingModelId(), ModelConfig.TYPE_EMBEDDING, "向量模型");
            setting.setEmbeddingModelId(request.embeddingModelId());
        }
        if (request.rerankEnabled() != null) {
            setting.setRerankEnabled(request.rerankEnabled());
        }
        if (request.rerankModelId() != null) {
            assertModelType(request.rerankModelId(), ModelConfig.TYPE_RERANK, "重排模型");
            setting.setRerankModelId(request.rerankModelId());
        }
        if (request.chunkSize() != null) {
            setting.setChunkSize(request.chunkSize());
        }
        if (request.chunkOverlap() != null) {
            setting.setChunkOverlap(request.chunkOverlap());
        }
        if (request.retrievalTopK() != null) {
            setting.setRetrievalTopK(request.retrievalTopK());
        }
        if (request.retrievalTopN() != null) {
            setting.setRetrievalTopN(request.retrievalTopN());
        }
        if (request.webSearchEndpoint() != null) {
            String endpoint = request.webSearchEndpoint().trim();
            if (!endpoint.isEmpty() && !endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                throw BusinessException.badRequest("搜索接口地址必须以 http:// 或 https:// 开头");
            }
            setting.setWebSearchEndpoint(endpoint.isEmpty() ? null : endpoint);
        }
        if (StringUtils.hasText(request.webSearchApiKey())) {
            setting.setWebSearchApiKey(cryptoService.encrypt(request.webSearchApiKey().trim()));
        }
        if (Boolean.TRUE.equals(request.clearWebSearchApiKey())) {
            setting.setWebSearchApiKey(null);
        }
        validate(setting);
        setting.setUpdatedBy(operatorId);
        setting.setUpdateTime(LocalDateTime.now());
        mapper.updateById(setting);
        return toResponse(setting);
    }

    private void validate(RagSetting setting) {
        if (setting.getChunkSize() == null || setting.getChunkSize() < 100) {
            throw BusinessException.badRequest("chunk_size 不能小于 100");
        }
        if (setting.getChunkOverlap() == null || setting.getChunkOverlap() < 0
                || setting.getChunkOverlap() >= setting.getChunkSize()) {
            throw BusinessException.badRequest("chunk_overlap 必须大于等于 0 且小于 chunk_size");
        }
        if (setting.getRetrievalTopK() == null || setting.getRetrievalTopK() < setting.getRetrievalTopN()) {
            throw BusinessException.badRequest("top_k 必须大于等于 top_n");
        }
        if (setting.rerankEnabled() && setting.getRerankModelId() == null) {
            throw BusinessException.badRequest("启用重排后必须选择重排模型");
        }
    }

    private void assertModelType(Long modelId, String type, String label) {
        ModelConfig model = modelConfigMapper.selectById(modelId);
        if (model == null || !type.equals(model.getModelType())) {
            throw BusinessException.badRequest(label + "选择不正确");
        }
    }

    private RagSettingResponse toResponse(RagSetting setting) {
        return new RagSettingResponse(setting.getChatModelId(), setting.getEmbeddingModelId(),
                setting.rerankEnabled(), setting.getRerankModelId(), setting.chunkSize(),
                setting.chunkOverlap(), setting.retrievalTopK(), setting.retrievalTopN(),
                setting.getWebSearchEndpoint(), StringUtils.hasText(setting.getWebSearchApiKey()),
                siteSettingService.formatDateTime(setting.getUpdateTime()));
    }
}
