package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.channel.ChannelNotificationRequest;
import bao.gabriel.cognia.system.dto.channel.ChannelNotificationResponse;

public interface ChannelNotificationService {

    ChannelNotificationResponse send(Long channelId, Long senderId, ChannelNotificationRequest request);
}
