package bao.gabriel.cognia.common.ai;

import bao.gabriel.cognia.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal OpenAI-compatible HTTP client. It intentionally does not depend on
 * Spring AI's auto-configured model beans: administrators can register any
 * vendor that speaks the Chat Completions protocol, with per-model extra body
 * parameters for vendor-specific thinking switches.
 */
@Slf4j
@Component
public class OpenAiCompatibleClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ObjectMapper mapper() {
        return JSON;
    }

    public String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.badRequest("API 端点不能为空");
        }
        String base = raw.trim().replaceAll("/+$", "");
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw BusinessException.badRequest("API 端点必须以 http:// 或 https:// 开头");
        }
        URI uri;
        try {
            uri = URI.create(base);
        } catch (Exception ex) {
            throw BusinessException.badRequest("API 端点格式不正确");
        }
        if (uri.getHost() == null) {
            throw BusinessException.badRequest("API 端点缺少主机名");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.endsWith("/v1") && !path.contains("/v1/")) {
            base = base + "/v1";
        }
        return base;
    }

    public JsonNode getJson(String baseUrl, String path, String apiKey) {
        return getJson(baseUrl, path, apiKey, null);
    }

    /**
     * GET with protocol specific authentication headers (e.g. Anthropic's
     * x-api-key + anthropic-version). When a header map is supplied the bearer
     * token is not added.
     */
    public JsonNode getJson(String baseUrl, String path, String apiKey, java.util.Map<String, String> headers) {
        HttpRequest.Builder builder;
        if (headers == null || headers.isEmpty()) {
            builder = request(baseUrl, path, apiKey);
        } else {
            builder = HttpRequest.newBuilder().uri(URI.create(normalizeBaseUrl(baseUrl) + path));
            headers.forEach(builder::header);
        }
        HttpRequest request = builder.GET().timeout(Duration.ofSeconds(30)).build();
        return send(request, null);
    }

    public JsonNode postJson(String baseUrl, String path, String apiKey, ObjectNode body, Duration timeout) {
        try {
            HttpRequest request = request(baseUrl, path, apiKey)
                    .timeout(timeout == null ? Duration.ofSeconds(120) : timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return send(request, null);
        } catch (IOException ex) {
            throw BusinessException.badRequest("请求模型服务失败：" + ex.getMessage());
        }
    }

    public HttpResponse<InputStream> stream(String baseUrl, String path, String apiKey, ObjectNode body) {
        return stream(baseUrl, path, apiKey, body, null);
    }

    /**
     * Streaming POST with protocol specific headers (Anthropic uses x-api-key +
     * anthropic-version instead of a bearer token).
     */
    public HttpResponse<InputStream> stream(String baseUrl, String path, String apiKey, ObjectNode body,
                                            java.util.Map<String, String> headers) {
        try {
            HttpRequest.Builder builder;
            if (headers == null || headers.isEmpty()) {
                builder = request(baseUrl, path, apiKey);
            } else {
                builder = HttpRequest.newBuilder().uri(URI.create(normalizeBaseUrl(baseUrl) + path));
                headers.forEach(builder::header);
            }
            HttpRequest request = builder
                    .timeout(Duration.ofMinutes(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception ex) {
            throw BusinessException.badRequest("请求模型服务失败：" + ex.getMessage());
        }
    }

    private HttpRequest.Builder request(String baseUrl, String path, String apiKey) {
        String base = normalizeBaseUrl(baseUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        return builder;
    }

    private JsonNode send(HttpRequest request, Void ignored) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BusinessException.badRequest(describeError(response.statusCode(), response.body()));
            }
            if (response.body() == null || response.body().isBlank()) {
                return JSON.createObjectNode();
            }
            return JSON.readTree(response.body());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("模型服务连接失败：" + ex.getMessage());
        }
    }

    public String describeError(int status, String body) {
        String message = body;
        try {
            JsonNode node = JSON.readTree(body == null ? "" : body);
            JsonNode error = node.path("error");
            if (error.isObject()) {
                message = error.path("message").asText(error.toString());
            } else if (error.isTextual()) {
                message = error.asText();
            } else if (node.path("message").isTextual()) {
                message = node.path("message").asText();
            }
        } catch (Exception ignored) {
            // keep raw body
        }
        if (message == null || message.isBlank()) {
            message = "模型服务返回错误";
        }
        String suffix = message.length() > 500 ? message.substring(0, 500) : message;
        return "模型服务错误（HTTP " + status + "）：" + suffix;
    }
}
