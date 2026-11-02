package bao.gabriel.cognia.system.dto.auth;

/**
 * @param devCode only populated when {@code app.mail.expose-code=true} (local development)
 */
public record SendCodeResponse(
        String message,
        long expiresInSeconds,
        long resendAfterSeconds,
        String devCode
) {
}
