package bao.gabriel.cognia.system.controller;

import bao.gabriel.cognia.common.api.ApiResponse;
import bao.gabriel.cognia.security.SecurityUtils;
import bao.gabriel.cognia.system.dto.search.SearchResponse;
import bao.gabriel.cognia.system.service.GlobalSearchService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam("q") @Size(max = 64) String q) {
        return ApiResponse.success(globalSearchService.search(q, SecurityUtils.getUserId()));
    }
}
