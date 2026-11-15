package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.channel.ChannelDetailResponse;
import bao.gabriel.cognia.system.dto.recycle.RecycleBinResponse;
import bao.gabriel.cognia.system.service.ChannelService;
import bao.gabriel.cognia.system.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Channel-scoped recycle bin. Only channel managers can list or restore files;
 * permanent deletion is reserved for the admin console.
 */
@RestController
@RequestMapping("/api/v1/channels/{channelId}/recycle")
@RequiredArgsConstructor
public class ChannelRecycleController {

    private final ChannelService channelService;
    private final RecycleBinService recycleBinService;

    @GetMapping
    public ApiResponse<List<RecycleBinResponse>> list(@PathVariable Long channelId) {
        requireManager(channelId);
        return ApiResponse.success(recycleBinService.listByChannel(channelId));
    }

    @PostMapping("/{entryId}/restore")
    public ApiResponse<Void> restore(@PathVariable Long channelId, @PathVariable Long entryId) {
        requireManager(channelId);
        recycleBinService.restore(entryId, channelId);
        return ApiResponse.success();
    }

    private void requireManager(Long channelId) {
        ChannelDetailResponse detail = channelService.detail(channelId, SecurityUtils.getUserId());
        if (!detail.canManage()) {
            throw BusinessException.forbidden("只有频道管理员可以操作回收站");
        }
    }
}
