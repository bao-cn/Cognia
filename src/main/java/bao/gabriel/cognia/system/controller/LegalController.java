package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.legal.ConsentStatusResponse;
import bao.gabriel.cognia.system.dto.legal.LegalDocumentResponse;
import bao.gabriel.cognia.system.dto.legal.SubmitConsentRequest;
import bao.gabriel.cognia.system.dto.legal.UpdateLegalDocumentRequest;
import bao.gabriel.cognia.system.service.LegalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalService legalService;

    @GetMapping("/documents")
    public ApiResponse<List<LegalDocumentResponse>> documents() {
        return ApiResponse.success(legalService.listDocuments());
    }

    @GetMapping("/consent")
    public ApiResponse<ConsentStatusResponse> consentStatus() {
        return ApiResponse.success(legalService.getConsentStatus(SecurityUtils.getUserId()));
    }

    @PostMapping("/consent")
    public ApiResponse<ConsentStatusResponse> submitConsent(@Valid @RequestBody SubmitConsentRequest request) {
        return ApiResponse.success(legalService.submitConsent(SecurityUtils.getUserId(), request));
    }

    /**
     * Publishing is explicit: content changes bump the version and force every
     * user to accept the documents again on their next visit.
     */
    @PutMapping("/{type}")
    @PreAuthorize("hasAuthority('settings:update')")
    public ApiResponse<LegalDocumentResponse> publish(@PathVariable String type,
                                                      @Valid @RequestBody UpdateLegalDocumentRequest request) {
        return ApiResponse.success(legalService.publish(type, request, SecurityUtils.getUserId()));
    }
}
