package bao.gabriel.cognia.system.dto.trace;

public record DebugSettingResponse(
        boolean enabled,
        String hardcodedUsername,
        String updatedAt
) {
}
