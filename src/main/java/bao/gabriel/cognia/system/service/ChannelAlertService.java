package bao.gabriel.cognia.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import bao.gabriel.cognia.system.entity.Channel;
import bao.gabriel.cognia.system.mapper.ChannelMapper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelAlertService {

    private final AlertRecipientService recipientService;
    private final EmailService emailService;
    private final ChannelMapper channelMapper;

    public void notifyAdmins(Long channelId, String subject, String message) {
        try {
            Channel channel = channelId == null ? null : channelMapper.selectById(channelId);
            String channelName = channel == null ? "频道" : channel.getName();
            for (String email : recipientService.channelAdminEmails(channelId)) {
                try {
                    emailService.sendChannelAlert(email, channelName, subject, message);
                } catch (Exception ex) {
                    log.warn("Channel alert email to {} failed: {}", email, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Resolve channel admins failed: {}", ex.getMessage());
        }
    }
}
