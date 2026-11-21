package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.system.service.BillingService;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/webhooks")
@RequiredArgsConstructor
public class BillingWebhookController {
    private final BillingService billingService;
    private final Environment environment;

    @PostMapping("/{provider}")
    public Map<String, Object> webhook(@PathVariable String provider,
                                       @RequestBody String raw,
                                       @RequestHeader(value = "X-Cognia-Signature", required = false) String signature,
                                       HttpServletRequest request) {
        Map<String, Object> payload = JSON.parseObject(raw);
        String providerKey = provider.toLowerCase().replace("-", "_");
        String secret = environment.getProperty("app.billing.webhook-secrets." + providerKey);
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("BILLING_" + provider.toUpperCase().replace("-", "_") + "_WEBHOOK_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH, "支付渠道 Webhook 密钥未配置");
        }
        if (!constantTimeHexHmac(secret, raw).equalsIgnoreCase(signature == null ? "" : signature)) {
            throw new BusinessException(ResultCode.PAYMENT_AMOUNT_MISMATCH, "支付渠道签名验证失败");
        }
        String eventId = stringValue(payload, "eventId", "event_id", "id");
        String transactionId = stringValue(payload, "transactionId", "tradeNo", "trade_no");
        String orderNo = stringValue(payload, "orderNo", "outTradeNo", "out_trade_no");
        long amount = longValue(payload, "amountMinor", "amount", "totalAmount");
        String currency = stringValue(payload, "currency", "feeType");
        if (currency == null || currency.isBlank()) currency = "CNY";
        if (eventId == null || eventId.isBlank()) eventId = transactionId;
        if (eventId == null || eventId.isBlank() || orderNo == null || orderNo.isBlank()) {
            throw BusinessException.badRequest("Webhook 缺少事件或订单标识");
        }
        String hash = sha256(raw);
        billingService.processPaymentWebhook(provider.toUpperCase(), eventId, transactionId, orderNo,
                amount, currency, hash, raw);
        return Map.of("success", true);
    }

    private static String stringValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private static long longValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null) continue;
            try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) {}
        }
        return 0L;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static String constantTimeHexHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
