package bao.gabriel.cognia.rag.ingestion;

import bao.gabriel.cognia.rag.service.VectorDimensionService;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.entity.ModelConfig;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import bao.gabriel.cognia.system.mapper.ModelConfigMapper;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.ChannelAlertService;
import bao.gabriel.cognia.system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingModelSwitchService {
    private final ModelConfigMapper modelConfigMapper;
    private final ChannelMapper channelMapper;
    private final NotificationService notificationService;
    private final ChannelAlertService channelAlertService;
    private final JdbcTemplate jdbcTemplate;
    private final VectorDimensionService dimensionService;
    private final ModelConfigService modelConfigService;

    public String modelLabel(Long modelId) {
        if (modelId == null) return null;
        try {
            var model = modelConfigService.resolveModel(modelId);
            return model.providerName() + "/" + model.modelId();
        } catch (Exception ex) {
            return null;
        }
    }

    public void validateSwitch(Long newModelId) {
        if (newModelId == null) return;
        ModelConfig model = modelConfigMapper.selectById(newModelId);
        if (model == null || !ModelConfig.TYPE_EMBEDDING.equals(model.getModelType())) {
            throw new IllegalArgumentException("向量模型选择不正确");
        }
        if (model.getDimension() != null && model.getDimension() != dimensionService.currentDimension()) {
            throw new IllegalStateException("目标向量模型维度为 " + model.getDimension()
                    + "，当前知识库为 " + dimensionService.currentDimension()
                    + " 维；请选择相同维度的模型，已有向量不会被清空。");
        }
    }

    public void onSwitch(Long oldModelId, Long newModelId) {
        if (oldModelId != null && oldModelId.equals(newModelId)) return;
        String newLabel = modelLabel(newModelId);
        if (newLabel == null) return;
        dimensionService.assertDimensionCompatible(newLabel);
        Integer restored = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM document_chunk WHERE embedding_legacy_model = ? AND embedding_legacy IS NOT NULL
                """, Integer.class, newLabel);
        if (restored != null && restored > 0) {
            jdbcTemplate.update("""
                    UPDATE document_chunk SET embedding = embedding_legacy, embedding_model = embedding_legacy_model,
                        embedding_status = 'READY'
                    WHERE embedding_legacy_model = ?
                    """, newLabel);
            log.info("Restored {} chunks from legacy embeddings for model {}", restored, newLabel);
            return;
        }
        jdbcTemplate.update("""
                UPDATE document_chunk SET embedding_legacy = embedding, embedding_legacy_model = embedding_model
                WHERE embedding IS NOT NULL
                """);
        jdbcTemplate.update("""
                UPDATE document_chunk SET embedding = NULL, embedding_status = 'PENDING'
                WHERE embedding IS NOT NULL
                """);
        List<Channel> channels = channelMapper.selectList(null);
        for (Channel channel : channels) {
            Long owner = channel.getOwnerType().equals("USER") ? channel.getOwnerId() : teamOwner(channel.getOwnerId());
            if (owner != null) {
                String content = "频道「" + channel.getName() + "」的向量模型已切换，需要重新向量化后才能用于检索。";
                notificationService.notify(owner, "CHANNEL_REEMBED_REQUIRED", "频道需要重新向量化",
                        content, Map.of("channelId", channel.getId(), "channelName", channel.getName()));
                channelAlertService.notifyAdmins(channel.getId(),
                        "【" + channel.getName() + "】频道需要重新向量化", content);
            }
        }
    }

    private Long teamOwner(Long teamId) {
        try {
            return jdbcTemplate.queryForObject("SELECT owner_id FROM sys_team WHERE id = ?", Long.class, teamId);
        } catch (Exception ex) { return null; }
    }
}
