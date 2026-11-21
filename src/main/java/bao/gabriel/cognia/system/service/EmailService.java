package bao.gabriel.cognia.system.service;

/**
 * Sends transactional mail. The active SMTP parameters are read from the
 * database on every send, so changing them in the console takes effect
 * without restarting the application.
 */
public interface EmailService {

    void sendRegisterCode(String email, String code, long ttlMinutes);

    void sendLoginCode(String email, String code, long ttlMinutes);

    void sendEmailChangeCode(String email, String code, long ttlMinutes);

    void sendTestEmail(String recipient);

    void sendAlert(String recipient, String subject, String message);

    void sendChannelAlert(String recipient, String channelName, String subject, String message);

    void sendChannelNotification(String recipient, String channelName, String subject, String content);

    void sendIssueNotification(String recipient, String channelName, String issueTitle, int issueNumber,
                               String actorName, String reason, String comment);
}
