package bao.gabriel.cognia.system.dto.tool;

public record ToolSettingResponse(
        boolean webSearchEnabled,
        String webSearchEndpoint,
        boolean webSearchApiKeyConfigured,
        boolean webFetchEnabled,
        int maxResults,
        String updatedAt
) {
}
