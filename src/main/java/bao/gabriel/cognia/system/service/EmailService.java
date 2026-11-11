package bao.gabriel.cognia.system.service;

/**
 * Sends transactional mail. The active SMTP parameters are read from the
 * database on every send, so changing them in the console takes effect
 * without restarting the application.
 */
public interface EmailService {

    void sendRegisterCode(String email, String code, long ttlMinutes);

    void sendEmailChangeCode(String email, String code, long ttlMinutes);

    void sendTestEmail(String recipient);
}
