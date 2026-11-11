package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.dashboard.ChannelInsightsResponse;
import bao.gabriel.cognia.system.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelInsightsController {
    private final AdminDashboardService dashboardService;

    @GetMapping("/{id}/insights")
    public ApiResponse<ChannelInsightsResponse> insights(@PathVariable Long id) {
        return ApiResponse.success(dashboardService.channelInsights(id, SecurityUtils.getUserId()));
    }
}
