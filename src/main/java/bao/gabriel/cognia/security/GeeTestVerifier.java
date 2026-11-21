package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * GeeTest 4.0 server-side verification.
 *
 * <p>The browser returns four fields after a successful challenge. The backend
 * signs {@code lot_number} with HMAC-SHA256 using the captcha key and posts the
 * payload plus {@code sign_token} to GeeTest's validation endpoint, exactly as
 * described in the official server deployment guide.
 */
@Slf4j
@Service
public class GeeTestVerifier {

    private static final String API_SERVER = "https://gcaptcha4.geetest.com";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final HttpClient httpClient;
    private final String apiServer;

    public GeeTestVerifier() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build(), API_SERVER);
    }

    GeeTestVerifier(HttpClient httpClient) {
        this(httpClient, API_SERVER);
    }

    GeeTestVerifier(HttpClient httpClient, String apiServer) {
        this.httpClient = httpClient;
        this.apiServer = apiServer;
    }

    public void verify(CaptchaSubmission submission, String captchaId, String captchaKey) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaKey)) {
            log.warn("GeeTest verification is required but the captcha id/key is missing");
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证尚未配置，请联系管理员");
        }

        String signToken = signToken(submission.lotNumber(), captchaKey);
        String form = "lot_number=" + encode(submission.lotNumber())
                + "&captcha_output=" + encode(submission.captchaOutput())
                + "&pass_token=" + encode(submission.passToken())
                + "&gen_time=" + encode(submission.genTime())
                + "&sign_token=" + encode(signToken);

        // captcha_id belongs in the query string so failed requests are easy to
        // trace back to the customer account in GeeTest's logs.
        URI uri = URI.create(apiServer + "/validate?captcha_id=" + encode(captchaId));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务暂不可用，请稍后重试");
        } catch (IOException ex) {
            log.warn("GeeTest verification request failed", ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务暂不可用，请稍后重试");
        }

        if (response.statusCode() != 200) {
            log.warn("GeeTest verification returned HTTP {}", response.statusCode());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务暂不可用，请稍后重试");
        }

        JSONObject payload;
        try {
            payload = JSON.parseObject(response.body());
        } catch (Exception ex) {
            log.warn("GeeTest verification returned an unreadable response", ex);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务异常，请稍后重试");
        }
        if (payload == null) {
            log.warn("GeeTest verification returned an empty response");
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务异常，请稍后重试");
        }

        String status = payload.getString("status");
        String result = payload.getString("result");
        if ("success".equals(status) && "success".equals(result)) {
            return;
        }
        if ("error".equals(status)) {
            // The request itself is malformed (wrong captcha_id, bad signature,
            // clock skew...): a configuration problem rather than a bad token.
            log.warn("GeeTest validation rejected the request: {} {}", payload.getString("code"),
                    payload.getString("msg"));
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "人机验证服务异常，请稍后重试");
        }
        log.warn("GeeTest verification failed: {}", payload.getString("reason"));
        throw BusinessException.badRequest("人机验证失败，请刷新后重试");
    }

    /**
     * {@code sign_token = hex(HMAC-SHA256(key = captcha_key, message = lot_number))}.
     */
    static String signToken(String lotNumber, String captchaKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(captchaKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(lotNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to sign the GeeTest validation payload", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
