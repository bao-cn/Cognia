package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.system.dto.model.OllamaPullStatusResponse;
import bao.gabriel.cognia.system.dto.model.OllamaStatusResponse;
import bao.gabriel.cognia.system.entity.ModelProvider;
import bao.gabriel.cognia.system.mapper.ModelProviderMapper;
import bao.gabriel.cognia.system.service.OllamaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaServiceImpl implements OllamaService {

    private final ModelProviderMapper providerMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    @Value("${OLLAMA_BASE_URL:${app.ollama.base-url:}}")
    private String configuredBaseUrl;

    @Override
    public OllamaStatusResponse status(Long providerId) {
        ModelProvider provider = resolve(providerId, true);
        if (provider == null) {
            return new OllamaStatusResponse(false, false, null, null, null, List.of(),
                    "尚未配置 Ollama，请添加供应商或启用本地 Ollama 服务。");
        }
        try {
            JsonNode body = get(nativeBase(provider.getBaseUrl()), "/api/tags");
            List<String> models = new ArrayList<>();
            for (JsonNode item : body.path("models")) {
                String name = item.path("name").asText("");
                if (!name.isBlank()) models.add(name);
            }
            return new OllamaStatusResponse(true, true, provider.getId(), provider.getName(),
                    provider.getBaseUrl(), models, "Ollama 已连接");
        } catch (Exception ex) {
            return new OllamaStatusResponse(true, false, provider.getId(), provider.getName(),
                    provider.getBaseUrl(), List.of(), "无法连接 Ollama：" + rootMessage(ex));
        }
    }

    @Override
    public OllamaPullStatusResponse pull(Long providerId, String model, boolean insecure) {
        ModelProvider provider = resolve(providerId, true);
        if (provider == null) throw new IllegalStateException("尚未配置 Ollama 供应商");
        String name = model == null ? "" : model.trim();
        if (name.isBlank()) throw new IllegalArgumentException("模型名称不能为空");
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Task task = new Task(taskId, name);
        tasks.put(taskId, task);
        Thread.ofVirtual().name("ollama-pull-" + taskId).start(() -> doPull(provider, task, insecure));
        return task.response();
    }

    @Override
    public OllamaPullStatusResponse pullStatus(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("拉取任务不存在");
        return task.response();
    }

    private void doPull(ModelProvider provider, Task task, boolean insecure) {
        task.status = "running";
        task.message = "正在拉取模型 " + task.model;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", task.model);
            body.put("stream", true);
            if (insecure) body.put("insecure", true);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nativeBase(provider.getBaseUrl()) + "/api/pull"))
                    .timeout(Duration.ofHours(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<InputStream> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                try (InputStream error = response.body()) {
                    String detail = new String(error.readAllBytes(), StandardCharsets.UTF_8);
                    throw new IllegalStateException("Ollama 返回 HTTP " + response.statusCode()
                            + (detail.isBlank() ? "" : "：" + detail.substring(0, Math.min(300, detail.length()))));
                }
            }
            boolean success = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode event;
                    try { event = objectMapper.readTree(line); } catch (Exception ignore) { continue; }
                    if (event.hasNonNull("error")) {
                        throw new IllegalStateException(event.path("error").asText());
                    }
                    String status = event.path("status").asText("");
                    if (!status.isBlank()) task.message = status;
                    if (event.has("total")) task.total = event.path("total").asLong();
                    if (event.has("completed")) task.completed = event.path("completed").asLong();
                    if (task.total != null && task.total > 0 && task.completed != null) {
                        task.percent = (int) Math.max(0, Math.min(100, task.completed * 100 / task.total));
                    }
                    if ("success".equalsIgnoreCase(status)) {
                        success = true;
                        task.percent = 100;
                    }
                }
            }
            if (!success) throw new IllegalStateException("模型拉取未完成");
            task.status = "succeeded";
            task.message = "模型拉取完成";
            task.done = true;
            log.info("Ollama model pulled: {}", task.model);
        } catch (Exception ex) {
            task.status = "failed";
            task.message = "模型拉取失败：" + rootMessage(ex);
            task.done = true;
            log.warn("Ollama pull failed: {}", ex.getMessage());
        }
    }

    private ModelProvider resolve(Long providerId, boolean createFromEnv) {
        if (providerId != null) {
            ModelProvider provider = providerMapper.selectById(providerId);
            if (provider != null) return provider;
        }
        ModelProvider provider = providerMapper.selectOne(new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getVendor, "OLLAMA")
                .orderByAsc(ModelProvider::getId).last("LIMIT 1"));
        if (provider != null) return provider;
        provider = providerMapper.selectOne(new LambdaQueryWrapper<ModelProvider>()
                .like(ModelProvider::getBaseUrl, "11434")
                .orderByAsc(ModelProvider::getId).last("LIMIT 1"));
        if (provider != null) return provider;
        return createFromEnv ? ensureConfiguredProvider() : null;
    }

    private ModelProvider ensureConfiguredProvider() {
        String base = configuredBaseUrl == null ? "" : configuredBaseUrl.trim();
        if (base.isBlank()) return null;
        if (!base.endsWith("/v1")) base = base.replaceAll("/+$", "") + "/v1";
        ModelProvider provider = new ModelProvider();
        provider.setName("Ollama（本地）");
        provider.setVendor("OLLAMA");
        provider.setBaseUrl(base);
        provider.setProtocol(ModelProvider.PROTOCOL_OPENAI_CHAT);
        provider.setEnabled(true);
        provider.setCreateTime(LocalDateTime.now());
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.insert(provider);
        return provider;
    }

    private JsonNode get(String base, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(4))
                .GET().build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private String nativeBase(String baseUrl) {
        String base = baseUrl == null ? "http://127.0.0.1:11434" : baseUrl.trim();
        return base.replaceAll("/v1/?$", "").replaceAll("/+$", "");
    }

    private String rootMessage(Exception ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String message = cur.getMessage();
        return message == null || message.isBlank() ? cur.getClass().getSimpleName() : message;
    }

    private static final class Task {
        private final String id;
        private final String model;
        private volatile String status = "queued";
        private volatile String message = "排队中";
        private volatile boolean done;
        private volatile Long completed;
        private volatile Long total;
        private volatile Integer percent;

        private Task(String id, String model) {
            this.id = id;
            this.model = model;
        }

        private OllamaPullStatusResponse response() {
            return new OllamaPullStatusResponse(id, model, status, message, done, completed, total, percent);
        }
    }
}
