package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.VectorDimensionService;
import bao.gabriel.cognia.rag.ingestion.EmbeddingHealthService;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.ModelConfigService.ResolvedModel;
import bao.gabriel.cognia.system.service.RagSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final int DEFAULT_DIMENSION = 1536;
    private static final int BATCH_SIZE = 16;
    private static final int MAX_CHARS = 8000;

    private final RagSettingService ragSettingService;
    private final ModelConfigService modelConfigService;
    private final OpenAiCompatibleClient client;
    private final VectorDimensionService dimensionService;
    private final EmbeddingHealthService healthService;

    @Override
    public EmbeddedText embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<EmbeddedText> embedAll(List<String> texts) {
        RagSetting setting = ragSettingService.getSetting();
        if (setting.getEmbeddingModelId() == null) {
            throw new IllegalStateException("尚未配置 Embedding 模型");
        }
        ResolvedModel model;
        try {
            model = modelConfigService.resolveModel(setting.getEmbeddingModelId());
        } catch (Exception ex) {
            throw new IllegalStateException("Embedding 模型不可用：" + ex.getMessage());
        }
        long started = System.currentTimeMillis();
        try {
            List<EmbeddedText> result = callModel(model, texts);
            if (result.isEmpty()) {
                throw new IllegalStateException("向量模型未返回 embedding 数据");
            }
            int actual = result.get(0).dimension();
            dimensionService.assertDimension(actual);
            healthService.record(activeModelLabel(), true, System.currentTimeMillis() - started, null);
            return result;
        } catch (Exception ex) {
            healthService.record(activeModelLabel(), false, System.currentTimeMillis() - started, ex.getMessage());
            log.warn("Embedding API failed: {}", ex.getMessage());
            throw new IllegalStateException("Embedding 服务调用失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public int dimension() {
        RagSetting setting = ragSettingService.getSetting();
        if (setting.getEmbeddingModelId() != null) {
            try {
                ResolvedModel model = modelConfigService.resolveModel(setting.getEmbeddingModelId());
                if (model.dimension() != null) {
                    return model.dimension();
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return dimensionService.currentDimension();
    }

    @Override
    public String activeModelLabel() {
        RagSetting setting = ragSettingService.getSetting();
        if (setting.getEmbeddingModelId() == null) {
            return null;
        }
        try {
            ResolvedModel model = modelConfigService.resolveModel(setting.getEmbeddingModelId());
            return model.providerName() + "/" + model.modelId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<EmbeddedText> callModel(ResolvedModel model, List<String> texts) throws Exception {
        List<EmbeddedText> result = new ArrayList<>();
        String label = model.providerName() + "/" + model.modelId();
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + BATCH_SIZE));
            ObjectNode body = client.mapper().createObjectNode();
            body.put("model", model.modelId());
            ArrayNode input = body.putArray("input");
            for (String text : batch) {
                input.add(trim(text));
            }
            JsonNode response = client.postJson(model.baseUrl(), "/embeddings", model.apiKey(), body,
                    java.time.Duration.ofSeconds(120));
            JsonNode data = response.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("向量模型未返回 embedding 数据");
            }
            int[] order = new int[data.size()];
            float[][] vectors = new float[data.size()][];
            for (int i = 0; i < data.size(); i++) {
                JsonNode item = data.get(i);
                order[i] = item.path("index").asInt(i);
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.isEmpty()) {
                    throw new IllegalStateException("向量模型返回的 embedding 为空");
                }
                float[] vector = new float[embedding.size()];
                for (int j = 0; j < embedding.size(); j++) {
                    vector[j] = (float) embedding.get(j).asDouble();
                }
                vectors[i] = vector;
            }
            for (int i = 0; i < batch.size(); i++) {
                int index = i < order.length ? order[i] : i;
                if (index < 0 || index >= vectors.length || vectors[index] == null) {
                    index = i;
                }
                float[] vector = vectors[Math.min(index, vectors.length - 1)];
                result.add(new EmbeddedText(vector, label, vector.length));
            }
        }
        return result;
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        return value.length() <= MAX_CHARS ? value : value.substring(0, MAX_CHARS);
    }
}
