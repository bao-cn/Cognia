package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Serves the original bytes of a knowledge-base document for citation previews.
 * The browser can display PDFs/images inline and the frontend renders Markdown
 * and DOCX client-side.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final ChannelService channelService;

    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> raw(@PathVariable Long id) {
        ChannelService.DocumentContent content = channelService.documentContent(id, SecurityUtils.getUserId());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String encoded = URLEncoder.encode(content.fileName() == null ? "document" : content.fileName(),
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .body(content.bytes());
    }
}
