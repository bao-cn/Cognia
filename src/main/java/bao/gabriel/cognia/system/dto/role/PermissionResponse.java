package bao.gabriel.cognia.system.dto.role;

public record PermissionResponse(
        String id,
        String code,
        String name,
        String type,
        String resource,
        String method,
        String parentId,
        Integer sort
) {
}
