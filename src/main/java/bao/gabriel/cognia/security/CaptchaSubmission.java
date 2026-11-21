package bao.gabriel.cognia.security;

import jakarta.validation.constraints.Size;

/**
 * Human-verification payload supplied by the client.
 *
 * <p>Cloudflare Turnstile only needs {@code token}; GeeTest 4.0 needs the four
 * validation fields returned by {@code captchaObj.getValidate()}. The backend
 * always verifies against the provider configured in the console and ignores
 * any client-side provider hint.
 */
public record CaptchaSubmission(
        @Size(max = 2048, message = "人机验证令牌过长")
        String token,

        @Size(max = 128, message = "极验验证流水号过长")
        String lotNumber,

        @Size(max = 4096, message = "极验验证输出过长")
        String captchaOutput,

        @Size(max = 4096, message = "极验验证标识过长")
        String passToken,

        @Size(max = 32, message = "极验验证时间戳过长")
        String genTime
) {

    /**
     * Adapts the legacy {@code turnstileToken} field used by older clients.
     */
    public static CaptchaSubmission turnstileToken(String token) {
        return token == null ? null : new CaptchaSubmission(token, null, null, null, null);
    }
}
