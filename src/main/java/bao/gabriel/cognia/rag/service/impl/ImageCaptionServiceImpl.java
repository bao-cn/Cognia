package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.common.ai.OpenAiCompatibleClient;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.rag.service.ImageCaptionService;
import bao.gabriel.cognia.system.entity.KbDocument;
import bao.gabriel.cognia.system.entity.RagSetting;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.PromptSettingService;
import bao.gabriel.cognia.system.service.RagSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCaptionServiceImpl implements ImageCaptionService {

    private final RagSettingService ragSettingService;
    private final ModelConfigService modelConfigService;
    private final PromptSettingService promptSettingService;
    private final OpenAiCompatibleClient client;

    @Override
    public String captionIfConfigured(KbDocument document, byte[] bytes) {
        RagSetting setting = ragSettingService.getSetting();
        if (!setting.imageEmbeddingEnabled() || setting.getImageCaptionModelId() == null) {
            return null;
        }
        var model = modelConfigService.resolveModel(setting.getImageCaptionModelId());
        if (!model.supportsVision()) {
            throw new IllegalStateException("图生文模型未开启图片输入");
        }
        String mime = mime(document);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        ObjectNode body = client.mapper().createObjectNode();
        body.put("model", model.modelId());
        body.put("temperature", 0.1);
        body.put("max_tokens", Math.max(1200, model.maxOutputTokensOrDefault()));
        // Captioning must return visible text, not spend the token budget on reasoning.
        if ("DEEPSEEK".equals(model.vendor())) {
            body.putObject("thinking").put("type", "disabled");
        } else if ("QWEN".equals(model.vendor())) {
            body.put("enable_thinking", false);
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", promptSettingService.getSetting().imageCaptionPrompt());
        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        image.putObject("image_url").put("url", dataUrl);
        JsonNode response = client.postJson(model.baseUrl(), "/chat/completions", model.apiKey(), body,
                Duration.ofSeconds(120));
        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        String text = contentNode.asText("");
        if (!StringUtils.hasText(text) && contentNode.isArray()) {
            StringBuilder joined = new StringBuilder();
            for (JsonNode part : contentNode) {
                String piece = part.path("text").asText("");
                if (StringUtils.hasText(piece)) joined.append(piece);
            }
            text = joined.toString();
        }
        if (!StringUtils.hasText(text)) {
            String reason = response.path("choices").path(0).path("finish_reason").asText("");
            throw new IllegalStateException("图生文模型返回空描述" + (reason.isBlank() ? "" : "（finish_reason=" + reason + "）"));
        }
        log.info("Image captioned for document {} ({} chars)", document.getId(), text.length());
        return text;
    }

    private String mime(KbDocument document) {
        String type = document.getContentType();
        if (StringUtils.hasText(type) && type.startsWith("image/")) return type;
        String name = document.getName() == null ? "" : document.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
