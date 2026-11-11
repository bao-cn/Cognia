package bao.gabriel.cognia.system.dto.tool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateToolSettingRequest(
        Boolean webSearchEnabled,
        @Size(max = 512) String webSearchEndpoint,
        @Size(max = 2048) String webSearchApiKey,
        Boolean clearWebSearchApiKey,
        Boolean webFetchEnabled,
        @Min(1) @Max(10) Integer maxResults
) {
}
