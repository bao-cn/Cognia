package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cloudflare Turnstile server-side verification. The provider and keys are
 * resolved by {@link CaptchaService}; this class only performs the handshake
 * with Cloudflare and translates the outcome into business errors.
 */
@Slf4j
@Service
public class TurnstileVerifier {

    private static final String VERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final HttpClient httpClient;

    public TurnstileVerifier() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    TurnstileVerifier(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void verify(String token, String secretKey, String remoteIp) {
        if (!StringUtils.hasText(secretKey)) {
            log.warn("Turnstile verification is required but the secret key is missing");
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证尚未配置，请联系管理员");
        }

        StringBuilder form = new StringBuilder()
                .append("secret=").append(encode(secretKey))
                .append("&response=").append(encode(token.trim()));
        if (StringUtils.hasText(remoteIp)) {
            form.append("&remoteip=").append(encode(remoteIp));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(VERIFY_URL))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务暂不可用，请稍后重试");
        } catch (IOException ex) {
            log.warn("Turnstile verification request failed", ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务暂不可用，请稍后重试");
        }

        if (response.statusCode() != 200) {
            log.warn("Turnstile verification returned HTTP {}", response.statusCode());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务暂不可用，请稍后重试");
        }

        JSONObject payload;
        try {
            payload = JSON.parseObject(response.body());
        } catch (Exception ex) {
            log.warn("Turnstile verification returned an unreadable response", ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务异常，请稍后重试");
        }

        if (payload == null || !payload.getBooleanValue("success")) {
            log.warn("Turnstile verification failed: {}", payload == null ? "empty response"
                    : payload.getJSONArray("error-codes"));
            throw BusinessException.badRequest("人机验证失败，请刷新后重试");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
