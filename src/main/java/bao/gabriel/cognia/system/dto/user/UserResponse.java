package bao.gabriel.cognia.system.dto.user;

import java.util.List;

public record UserResponse(
        String id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        Integer status,
        List<RoleSummary> roles,
        List<String> permissions,
        String lastLoginTime,
        String lastLoginIp,
        String createTime,
        String remark,
        boolean autoWebSearch,
        boolean twoFactorEnabled,
        boolean hasPassword
) {
}
