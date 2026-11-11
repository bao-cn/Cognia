package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.favorite.FavoriteListResponse;
import bao.gabriel.cognia.system.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<FavoriteListResponse> list() {
        return ApiResponse.success(favoriteService.list(SecurityUtils.getUserId()));
    }

    @PutMapping("/conversations/{conversationId}")
    public ApiResponse<Boolean> favoriteConversation(@PathVariable String conversationId) {
        return ApiResponse.success(favoriteService.addConversation(SecurityUtils.getUserId(), conversationId));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Boolean> unfavoriteConversation(@PathVariable String conversationId) {
        return ApiResponse.success(favoriteService.removeConversation(SecurityUtils.getUserId(), conversationId));
    }

    @PutMapping("/messages/{messageId}")
    public ApiResponse<Boolean> favoriteMessage(@PathVariable Long messageId) {
        return ApiResponse.success(favoriteService.addMessage(SecurityUtils.getUserId(), messageId));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Boolean> unfavoriteMessage(@PathVariable Long messageId) {
        return ApiResponse.success(favoriteService.removeMessage(SecurityUtils.getUserId(), messageId));
    }
}
