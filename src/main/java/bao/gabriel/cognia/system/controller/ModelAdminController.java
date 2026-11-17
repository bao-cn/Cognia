package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.model.ModelConfigResponse;
import bao.gabriel.cognia.system.dto.model.OllamaPullRequest;
import bao.gabriel.cognia.system.dto.model.OllamaPullStatusResponse;
import bao.gabriel.cognia.system.dto.model.OllamaStatusResponse;
import bao.gabriel.cognia.system.dto.model.ModelProviderResponse;
import bao.gabriel.cognia.system.dto.model.PublicModelResponse;
import bao.gabriel.cognia.system.dto.model.RemoteModelResponse;
import bao.gabriel.cognia.system.dto.model.SaveModelConfigRequest;
import bao.gabriel.cognia.system.dto.model.SaveModelProviderRequest;
import bao.gabriel.cognia.system.service.ModelConfigService;
import bao.gabriel.cognia.system.service.OllamaService;
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
@RequestMapping("/api/v1/admin/models")
@RequiredArgsConstructor
public class ModelAdminController {

    private final ModelConfigService modelConfigService;
    private final OllamaService ollamaService;

    @GetMapping("/providers")
    @PreAuthorize("hasAnyAuthority('model:read', 'settings:read')")
    public ApiResponse<List<ModelProviderResponse>> providers() {
        return ApiResponse.success(modelConfigService.listProviders());
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<ModelProviderResponse> createProvider(@Valid @RequestBody SaveModelProviderRequest request) {
        return ApiResponse.success(modelConfigService.createProvider(request, SecurityUtils.getUserId()));
    }

    @PutMapping("/providers/{id}")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<ModelProviderResponse> updateProvider(@PathVariable Long id,
                                                             @Valid @RequestBody SaveModelProviderRequest request) {
        return ApiResponse.success(modelConfigService.updateProvider(id, request, SecurityUtils.getUserId()));
    }

    @DeleteMapping("/providers/{id}")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<Void> deleteProvider(@PathVariable Long id) {
        modelConfigService.deleteProvider(id);
        return ApiResponse.success();
    }

    @PostMapping("/providers/{id}/fetch")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<List<RemoteModelResponse>> fetch(@PathVariable Long id) {
        return ApiResponse.success(modelConfigService.fetchRemoteModels(id));
    }

    @PostMapping("/providers/fetch")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<List<RemoteModelResponse>> fetchAdHoc(@RequestBody java.util.Map<String, String> body) {
        return ApiResponse.success(modelConfigService.fetchRemoteModels(body.get("baseUrl"), body.get("apiKey"),
                body.get("protocol")));
    }

    @GetMapping("/configs")
    @PreAuthorize("hasAnyAuthority('model:read', 'settings:read')")
    public ApiResponse<List<ModelConfigResponse>> models(@RequestParam(required = false) Long providerId) {
        return ApiResponse.success(modelConfigService.listModels(providerId));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<ModelConfigResponse> saveModel(@Valid @RequestBody SaveModelConfigRequest request) {
        return ApiResponse.success(modelConfigService.saveModel(request));
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<Void> deleteModel(@PathVariable Long id) {
        modelConfigService.deleteModel(id);
        return ApiResponse.success();
    }

    @GetMapping("/ollama/status")
    @PreAuthorize("hasAnyAuthority('model:read', 'settings:read')")
    public ApiResponse<OllamaStatusResponse> ollamaStatus(@RequestParam(required = false) Long providerId) {
        return ApiResponse.success(ollamaService.status(providerId));
    }

    @PostMapping("/ollama/pull")
    @PreAuthorize("hasAnyAuthority('model:update', 'settings:update')")
    public ApiResponse<OllamaPullStatusResponse> pullOllamaModel(@Valid @RequestBody OllamaPullRequest request) {
        return ApiResponse.success(ollamaService.pull(request.providerId(), request.model(), Boolean.TRUE.equals(request.insecure())));
    }

    @GetMapping("/ollama/pull/{taskId}")
    @PreAuthorize("hasAnyAuthority('model:read', 'settings:read')")
    public ApiResponse<OllamaPullStatusResponse> ollamaPullStatus(@PathVariable String taskId) {
        return ApiResponse.success(ollamaService.pullStatus(taskId));
    }

    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PublicModelResponse>> available() {
        return ApiResponse.success(modelConfigService.publicModels());
    }
}
