package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.dashboard.AdminDashboardResponse;
import bao.gabriel.cognia.system.dto.dashboard.ChannelInsightsResponse;

public interface AdminDashboardService {
    AdminDashboardResponse overview();
    ChannelInsightsResponse channelInsights(Long channelId, Long viewerId);
}
