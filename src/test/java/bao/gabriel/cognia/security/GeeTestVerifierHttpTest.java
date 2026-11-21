package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the GeeTest secondary-validation handshake against a local stub of
 * the gcaptcha4 endpoint, including the HMAC signature and error mapping.
 */
class GeeTestVerifierHttpTest {

    private final AtomicReference<String> requestQuery = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private volatile String responseBody = "{\"status\":\"success\",\"result\":\"success\"}";

    private HttpServer server;
    private GeeTestVerifier verifier;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/validate", exchange -> {
            requestQuery.set(exchange.getRequestURI().getQuery());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        verifier = new GeeTestVerifier(HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheSignedPayloadToTheValidationEndpoint() {
        CaptchaSubmission submission = new CaptchaSubmission(
                null, "lot-number", "captcha-output", "pass-token", "1700000000");

        assertDoesNotThrow(() -> verifier.verify(submission, "captcha-id", "captcha-key"));

        assertEquals("captcha_id=captcha-id", requestQuery.get());
        String body = requestBody.get();
        assertTrue(body.contains("lot_number=lot-number"));
        assertTrue(body.contains("captcha_output=captcha-output"));
        assertTrue(body.contains("pass_token=pass-token"));
        assertTrue(body.contains("gen_time=1700000000"));
        assertTrue(body.contains("sign_token=" + GeeTestVerifier.signToken("lot-number", "captcha-key")));
    }

    @Test
    void rejectsAFailedValidation() {
        responseBody = "{\"status\":\"success\",\"result\":\"fail\",\"reason\":\"pass_token expire\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> verifier.verify(validSubmission(), "captcha-id", "captcha-key"));
        assertEquals("人机验证失败，请刷新后重试", exception.getMessage());
    }

    @Test
    void mapsARequestErrorToAServiceError() {
        responseBody = "{\"status\":\"error\",\"code\":\"-50005\",\"msg\":\"illegal gen_time\"}";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> verifier.verify(validSubmission(), "captcha-id", "captcha-key"));
        assertEquals("人机验证服务异常，请稍后重试", exception.getMessage());
    }

    private CaptchaSubmission validSubmission() {
        return new CaptchaSubmission(null, "lot-number", "captcha-output", "pass-token", "1700000000");
    }
}
