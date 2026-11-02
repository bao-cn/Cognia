package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.role.PermissionResponse;
import bao.gabriel.cognia.system.entity.Permission;
import bao.gabriel.cognia.system.mapper.PermissionMapper;
import bao.gabriel.cognia.system.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    @Override
    public List<PermissionResponse> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                        .orderByAsc(Permission::getSort)
                        .orderByAsc(Permission::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<Permission> getPermissionsByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return permissionMapper.selectByUserId(userId);
    }

    @Override
    public Set<String> getPermissionCodesByUserId(Long userId) {
        return getPermissionsByUserId(userId).stream()
                .map(Permission::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void assertPermissionsExist(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = permissionIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return;
        }
        List<Permission> found = permissionMapper.selectByIds(uniqueIds);
        if (found.size() != uniqueIds.size()) {
            Set<Long> foundIds = found.stream().map(Permission::getId).collect(Collectors.toSet());
            String missing = uniqueIds.stream().filter(id -> !foundIds.contains(id))
                    .map(String::valueOf).collect(Collectors.joining(", "));
            throw BusinessException.badRequest("权限不存在: " + missing);
        }
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                String.valueOf(permission.getId()),
                permission.getCode(),
                permission.getName(),
                permission.getType(),
                permission.getResource(),
                permission.getMethod(),
                permission.getParentId() == null ? null : String.valueOf(permission.getParentId()),
                permission.getSort()
        );
    }
}
