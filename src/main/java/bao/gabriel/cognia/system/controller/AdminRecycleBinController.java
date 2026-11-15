package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.recycle.AdminRecycleBinResponse;
import bao.gabriel.cognia.system.dto.recycle.RecycleChannelOption;
import bao.gabriel.cognia.system.dto.recycle.RecycleFileContent;
import bao.gabriel.cognia.system.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Admin-only recycle bin. The channel page has a manager-scoped restore-only
 * view; permanent deletion and bulk inspection live here.
 */
@RestController
@RequestMapping("/api/v1/admin/recycle-bin")
@RequiredArgsConstructor
public class AdminRecycleBinController {

    private final RecycleBinService recycleBinService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('recycle:read', 'settings:read')")
    public ApiResponse<PageResult<AdminRecycleBinResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String channelKeyword) {
        return ApiResponse.success(recycleBinService.adminPage(page, size, channelId, keyword, channelKeyword));
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAnyAuthority('recycle:read', 'settings:read')")
    public ApiResponse<List<RecycleChannelOption>> channels() {
        return ApiResponse.success(recycleBinService.adminChannels());
    }

    @GetMapping("/{id}/raw")
    @PreAuthorize("hasAnyAuthority('recycle:read', 'settings:read')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        RecycleFileContent content = recycleBinService.adminContent(id);
        String name = content.name() == null || content.name().isBlank() ? "file" : content.name();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (content.contentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(content.contentType());
            } catch (Exception ignored) {
                // keep octet-stream for malformed stored content types
            }
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.bytes());
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyAuthority('recycle:update', 'settings:update')")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        recycleBinService.adminRestore(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('recycle:update', 'settings:update')")
    public ApiResponse<Void> purge(@PathVariable Long id) {
        recycleBinService.adminPurge(id);
        return ApiResponse.success();
    }
}
