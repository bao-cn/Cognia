package bao.gabriel.cognia.system.dto.settings;

/**
 * Cloudflare Turnstile capability. {@code configured} is true when both keys
 * are present; the register/login switches decide where verification is
 * actually required.
 */
public record TurnstileConfig(boolean configured, String siteKey, String secretKey) {
}
