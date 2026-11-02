package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.recycle.RecycleBinResponse;
import bao.gabriel.cognia.system.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    @GetMapping
    public ApiResponse<List<RecycleBinResponse>> list(@RequestParam String ownerType,
                                                      @RequestParam Long ownerId) {
        return ApiResponse.success(recycleBinService.list(ownerType, ownerId, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> purge(@PathVariable Long id) {
        recycleBinService.purge(id, SecurityUtils.getUserId());
        return ApiResponse.success();
    }
}
