package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.system.dto.dashboard.AdminDashboardResponse;
import bao.gabriel.cognia.system.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('dashboard:read', 'user:read', 'settings:read')")
    public ApiResponse<AdminDashboardResponse> overview() {
        return ApiResponse.success(dashboardService.overview());
    }
}
