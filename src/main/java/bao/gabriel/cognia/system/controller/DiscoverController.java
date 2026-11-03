package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.discover.DiscoverChannelResponse;
import bao.gabriel.cognia.system.dto.discover.FeaturedRequest;
import bao.gabriel.cognia.system.service.DiscoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/discover")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverService discoverService;

    @GetMapping("/channels")
    public ApiResponse<PageResult<DiscoverChannelResponse>> channels(
            @RequestParam(defaultValue = "featured") String tab,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "9") long size) {
        return ApiResponse.success(discoverService.channels(tab, period, page, size, viewerId()));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<DiscoverChannelResponse>> subscriptions() {
        return ApiResponse.success(discoverService.subscriptions(SecurityUtils.getUserId()));
    }

    @PostMapping("/channels/{id}/subscribe")
    public ApiResponse<Void> subscribe(@PathVariable Long id) {
        discoverService.subscribe(id, SecurityUtils.getUserId());
        return ApiResponse.success();
    }

    @DeleteMapping("/channels/{id}/subscribe")
    public ApiResponse<Void> unsubscribe(@PathVariable Long id) {
        discoverService.unsubscribe(id, SecurityUtils.getUserId());
        return ApiResponse.success();
    }

    @PostMapping("/channels/{id}/notify-subscription")
    public ApiResponse<Void> subscribeNotify(@PathVariable Long id) {
        discoverService.subscribeNotify(id, SecurityUtils.getUserId());
        return ApiResponse.success();
    }

    @DeleteMapping("/channels/{id}/notify-subscription")
    public ApiResponse<Void> unsubscribeNotify(@PathVariable Long id) {
        discoverService.unsubscribeNotify(id, SecurityUtils.getUserId());
        return ApiResponse.success();
    }

    @PutMapping("/channels/{id}/featured")
    @PreAuthorize("hasAnyAuthority('settings:update','channel:update')")
    public ApiResponse<Void> setFeatured(@PathVariable Long id,
                                         @Valid @RequestBody FeaturedRequest request) {
        discoverService.setFeatured(id, request.featured());
        return ApiResponse.success();
    }

    private Long viewerId() {
        return SecurityUtils.getLoginUserOrNull() == null ? null : SecurityUtils.getUserId();
    }
}
