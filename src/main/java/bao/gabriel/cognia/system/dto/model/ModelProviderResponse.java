package bao.gabriel.cognia.system.dto.model;

public record ModelProviderResponse(
        Long id,
        String name,
        String vendor,
        String baseUrl,
        boolean apiKeyConfigured,
        boolean enabled,
        long modelCount,
        String createTime,
        String updatedAt
) {
}
