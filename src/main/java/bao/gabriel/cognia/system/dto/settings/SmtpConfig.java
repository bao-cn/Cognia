package bao.gabriel.cognia.system.dto.settings;

/**
 * Decrypted SMTP configuration used by the mail sender.
 */
public record SmtpConfig(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String from,
        boolean ssl,
        boolean starttls,
        boolean auth
) {
    public boolean usable() {
        return enabled && host != null && !host.isBlank();
    }
}
