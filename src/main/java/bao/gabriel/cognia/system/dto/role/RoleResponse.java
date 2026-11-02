package bao.gabriel.cognia.system.dto.role;

import java.util.List;

public record RoleResponse(
        String id,
        String code,
        String name,
        String description,
        Integer sort,
        Integer status,
        Boolean builtin,
        List<String> permissionIds,
        long userCount,
        String createTime
) {
}
