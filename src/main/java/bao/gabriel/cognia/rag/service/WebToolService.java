package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.entity.ToolSetting;
import bao.gabriel.cognia.system.service.ToolSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebToolService {

    private static final int MAX_FETCH_CHARS = 20000;
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style|noscript)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern ENTITY = Pattern.compile("&(amp|lt|gt|quot|#39|nbsp);");

    private final OpenAiCompatibleClient client;
    private final ToolSettingService toolSettingService;
    private final SecurityCryptoService cryptoService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Declares only the tools that are actually usable. web_search requires a
     * configured endpoint: there is no implicit third-party fallback.
     */
    public ArrayNode toolDefinitions() {
        ArrayNode tools = mapper.createArrayNode();
        ToolSetting setting = toolSettingService.getSetting();
        if (setting.webSearchUsable()) {
            ObjectNode search = tools.addObject();
            search.put("type", "function");
            ObjectNode searchFn = search.putObject("function");
            searchFn.put("name", "web_search");
            searchFn.put("description", "Search the web for up-to-date information. Use concise keywords and cite the returned URLs.");
            ObjectNode searchParams = searchFn.putObject("parameters");
            searchParams.put("type", "object");
            ObjectNode searchProps = searchParams.putObject("properties");
            searchProps.putObject("query").put("type", "string").put("description", "Search keywords");
            searchProps.putObject("max_results").put("type", "integer")
                    .put("description", "1-10, default " + setting.maxResultsOr(5));
            searchParams.putArray("required").add("query");
        }
        if (!setting.webFetchEnabled()) {
            return tools;
        }

        ObjectNode fetch = tools.addObject();
        fetch.put("type", "function");
        ObjectNode fetchFn = fetch.putObject("function");
        fetchFn.put("name", "web_fetch");
        fetchFn.put("description", "Fetch a single web page and return its readable Markdown-like text.");
        ObjectNode fetchParams = fetchFn.putObject("parameters");
        fetchParams.put("type", "object");
        ObjectNode fetchProps = fetchParams.putObject("properties");
        fetchProps.putObject("url").put("type", "string").put("description", "Absolute http(s) URL");
        fetchParams.putArray("required").add("url");

        return tools;
    }

    public String execute(String name, String arguments, String fallbackApiKey) {
        JsonNode args;
        try {
            args = mapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        } catch (Exception ex) {
            return "参数解析失败：" + ex.getMessage();
        }
        if ("web_search".equals(name)) {
            return webSearch(args.path("query").asText(""), args.path("max_results").asInt(5), fallbackApiKey);
        }
        if ("web_fetch".equals(name)) {
            return webFetch(args.path("url").asText(""));
        }
        return "未知工具：" + name;
    }

    public String webSearch(String query, int maxResults, String fallbackApiKey) {
        if (!StringUtils.hasText(query)) {
            return "搜索关键词为空";
        }
        ToolSetting setting = toolSettingService.getSetting();
        if (!setting.webSearchUsable()) {
            return "联网搜索未配置，本次未执行搜索";
        }
        int limit = Math.max(1, Math.min(10, maxResults));
        String endpoint = setting.getWebSearchEndpoint();
        String key = clientKey(setting, fallbackApiKey);
        try {
            HttpResponse<String> response;
            if (endpoint.contains("deepseek.com")) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("query", query);
                payload.put("max_results", limit);
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
                if (StringUtils.hasText(key)) {
                    builder.header("Authorization", "Bearer " + key);
                }
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } else {
                String url = endpoint.contains("{query}")
                        ? endpoint.replace("{query}", URLEncoder.encode(query, StandardCharsets.UTF_8))
                        : endpoint + (endpoint.contains("?") ? "&" : "?") + "q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=" + limit;
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20)).GET();
                if (StringUtils.hasText(key)) {
                    builder.header("Authorization", "Bearer " + key);
                }
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return truncate(response.body(), MAX_FETCH_CHARS);
            }
            log.warn("Configured web_search endpoint returned HTTP {}", response.statusCode());
            return "搜索服务不可用（HTTP " + response.statusCode() + "）";
        } catch (Exception ex) {
            log.warn("web_search failed: {}", ex.getMessage());
            return "搜索失败：" + ex.getMessage();
        }
    }

    public String webFetch(String url) {
        if (!StringUtils.hasText(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return "无效的 URL";
        }
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Cognia/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml,text/plain")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "抓取失败（HTTP " + response.statusCode() + "）";
            }
            String body = response.body();
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (contentType.contains("html")) {
                String title = "";
                Matcher titleMatcher = TITLE.matcher(body);
                if (titleMatcher.find()) {
                    title = stripTags(titleMatcher.group(1));
                }
                body = "# " + title + "\n\n" + htmlToText(body);
            }
            return truncate("URL: " + url + "\n\n" + body, MAX_FETCH_CHARS);
        } catch (Exception ex) {
            return "抓取失败：" + ex.getMessage();
        }
    }

    private String htmlToText(String html) {
        String cleaned = SCRIPT.matcher(html).replaceAll(" ");
        cleaned = TAG.matcher(cleaned).replaceAll("\n");
        cleaned = ENTITY.matcher(cleaned).replaceAll(match -> switch (match.group(1)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "#39" -> "'";
            default -> " ";
        });
        return cleaned.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n")
                .trim();
    }

    private String stripTags(String value) {
        return ENTITY.matcher(TAG.matcher(value == null ? "" : value).replaceAll(" "))
                .replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private String clientKey(ToolSetting setting, String fallbackApiKey) {
        if (StringUtils.hasText(setting.getWebSearchApiKey())) {
            String decrypted = cryptoService.decrypt(setting.getWebSearchApiKey());
            if (StringUtils.hasText(decrypted)) {
                return decrypted;
            }
        }
        return fallbackApiKey;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "\n...[truncated]";
    }
}
