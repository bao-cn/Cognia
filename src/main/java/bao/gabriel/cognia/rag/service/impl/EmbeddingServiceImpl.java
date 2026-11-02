package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.rag.service.EmbeddingService;
import bao.gabriel.cognia.rag.service.VectorDimensionService;
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

    @Override
    public EmbeddedText embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<EmbeddedText> embedAll(List<String> texts) {
        RagSetting setting = ragSettingService.getSetting();
        ResolvedModel model = null;
        if (setting.getEmbeddingModelId() != null) {
            try {
                model = modelConfigService.resolveModel(setting.getEmbeddingModelId());
            } catch (Exception ex) {
                log.warn("Configured embedding model is unavailable: {}", ex.getMessage());
            }
        }
        int expectedDimension = model != null && model.dimension() != null
                ? model.dimension() : dimensionService.currentDimension();
        if (model != null) {
            try {
                List<EmbeddedText> result = callModel(model, texts);
                if (!result.isEmpty()) {
                    int actual = result.get(0).dimension();
                    dimensionService.ensureDimension(actual);
                    return result;
                }
            } catch (Exception ex) {
                log.warn("Embedding API failed, falling back to local hash embeddings: {}", ex.getMessage());
            }
        }
        dimensionService.ensureDimension(expectedDimension);
        List<EmbeddedText> fallback = new ArrayList<>(texts.size());
        for (String text : texts) {
            fallback.add(new EmbeddedText(localEmbedding(text, expectedDimension),
                    model == null ? "local-hash" : model.providerName() + "/" + model.modelId() + " (fallback)",
                    expectedDimension));
        }
        return fallback;
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

    private float[] localEmbedding(String text, int dimension) {
        float[] vector = new float[dimension];
        String normalized = (text == null ? "" : text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            vector[0] = 1f;
            return vector;
        }
        if (normalized.length() == 1) {
            vector[Math.floorMod(normalized.hashCode(), dimension)] = 1f;
        }
        for (int i = 0; i + 1 < normalized.length(); i++) {
            String gram = normalized.substring(i, i + 2);
            int hash = gram.hashCode();
            vector[Math.floorMod(hash, dimension)] += 1f;
            vector[Math.floorMod(hash * 31 + 17, dimension)] += 0.5f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[0] = 1f;
            return vector;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
