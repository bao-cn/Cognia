package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.team.AdminTeamResponse;
import bao.gabriel.cognia.system.service.AdminTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/teams")
@RequiredArgsConstructor
public class AdminTeamController {

    private final AdminTeamService adminTeamService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('team:read', 'user:read')")
    public ApiResponse<PageResult<AdminTeamResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size,
                                                           @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminTeamService.page(page, size, keyword));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('team:update', 'user:update')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminTeamService.delete(id);
        return ApiResponse.success();
    }
}
