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
            setting.setWebSearchEnabled(true);
            setting.setAllowUserWebSearch(true);
            setting.setContextCompressionThreshold(80);
            setting.setCompressionPrompt(RagSetting.DEFAULT_COMPRESSION_PROMPT);
            setting.setSemanticCacheEnabled(true);
            setting.setRetrievalCacheEnabled(true);
            setting.setSemanticCacheThreshold(0.92);
            setting.setSemanticCacheTtlMinutes(60);
            setting.setSemanticCacheMaxEntries(500);
            setting.setRetrievalCacheTtlMinutes(1440);
            setting.setRetrievalCacheMaxEntries(1000);
            setting.setTitleGenerationEnabled(true);
            setting.setUpdateTime(LocalDateTime.now());
            mapper.insert(setting);
        }
        return setting;
    }

    @Override
    public boolean webSearchEnabled() {
        return getSetting().webSearchEnabled();
    }

    @Override
    public boolean allowUserWebSearch() {
        return getSetting().allowUserWebSearch();
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
        if (request.webSearchEnabled() != null) {
            setting.setWebSearchEnabled(request.webSearchEnabled());
        }
        if (request.allowUserWebSearch() != null) {
            setting.setAllowUserWebSearch(request.allowUserWebSearch());
        }
        if (request.contextCompressionThreshold() != null) {
            setting.setContextCompressionThreshold(request.contextCompressionThreshold());
        }
        if (request.semanticCacheEnabled() != null) {
            setting.setSemanticCacheEnabled(request.semanticCacheEnabled());
        }
        if (request.retrievalCacheEnabled() != null) {
            setting.setRetrievalCacheEnabled(request.retrievalCacheEnabled());
        }
        if (request.semanticCacheThreshold() != null) {
            setting.setSemanticCacheThreshold(request.semanticCacheThreshold());
        }
        if (request.semanticCacheTtlMinutes() != null) {
            setting.setSemanticCacheTtlMinutes(request.semanticCacheTtlMinutes());
        }
        if (request.semanticCacheMaxEntries() != null) {
            setting.setSemanticCacheMaxEntries(request.semanticCacheMaxEntries());
        }
        if (request.retrievalCacheTtlMinutes() != null) {
            setting.setRetrievalCacheTtlMinutes(request.retrievalCacheTtlMinutes());
        }
        if (request.retrievalCacheMaxEntries() != null) {
            setting.setRetrievalCacheMaxEntries(request.retrievalCacheMaxEntries());
        }
        if (request.titleGenerationEnabled() != null) {
            setting.setTitleGenerationEnabled(request.titleGenerationEnabled());
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
        if (setting.getContextCompressionThreshold() != null
                && (setting.getContextCompressionThreshold() < 1 || setting.getContextCompressionThreshold() > 100)) {
            throw BusinessException.badRequest("上下文压缩阈值必须在 1 到 100 之间");
        }
        if (setting.getSemanticCacheThreshold() != null
                && (setting.getSemanticCacheThreshold() < 0.5 || setting.getSemanticCacheThreshold() > 1.0)) {
            throw BusinessException.badRequest("语义缓存相似度阈值必须在 0.5 到 1.0 之间");
        }
        if (setting.getSemanticCacheTtlMinutes() != null && setting.getSemanticCacheTtlMinutes() < 1) {
            throw BusinessException.badRequest("语义缓存 TTL 必须大于 0");
        }
        if (setting.getRetrievalCacheTtlMinutes() != null && setting.getRetrievalCacheTtlMinutes() < 1) {
            throw BusinessException.badRequest("检索缓存 TTL 必须大于 0");
        }
        if (setting.getSemanticCacheMaxEntries() != null && setting.getSemanticCacheMaxEntries() < 1) {
            throw BusinessException.badRequest("语义缓存条目上限必须大于 0");
        }
        if (setting.getRetrievalCacheMaxEntries() != null && setting.getRetrievalCacheMaxEntries() < 1) {
            throw BusinessException.badRequest("检索缓存条目上限必须大于 0");
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
                siteSettingService.formatDateTime(setting.getUpdateTime()),
                setting.webSearchEnabled(), setting.allowUserWebSearch(),
                setting.contextCompressionThreshold(),
                setting.semanticCacheEnabled(), setting.retrievalCacheEnabled(),
                setting.semanticCacheThreshold(), setting.semanticCacheTtlMinutes(),
                setting.semanticCacheMaxEntries(), setting.retrievalCacheTtlMinutes(),
                setting.retrievalCacheMaxEntries(), setting.titleGenerationEnabled());
    }
}
