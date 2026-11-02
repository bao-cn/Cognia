package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.TokenService;
import bao.gabriel.cognia.system.dto.role.RoleRequest;
import bao.gabriel.cognia.system.dto.role.RoleResponse;
import bao.gabriel.cognia.system.dto.user.RoleSummary;
import bao.gabriel.cognia.system.entity.Role;
import bao.gabriel.cognia.system.entity.RolePermission;
import bao.gabriel.cognia.system.mapper.RoleMapper;
import bao.gabriel.cognia.system.mapper.RolePermissionMapper;
import bao.gabriel.cognia.system.mapper.UserRoleMapper;
import bao.gabriel.cognia.system.service.PermissionService;
import bao.gabriel.cognia.system.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final TokenService tokenService;

    @Override
    public List<RoleResponse> listRoles(String keyword) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Role::getCode, trimmed).or().like(Role::getName, trimmed));
        }
        wrapper.orderByAsc(Role::getSort).orderByAsc(Role::getId);
        return roleMapper.selectList(wrapper).stream().map(this::toResponse).toList();
    }

    @Override
    public RoleResponse getRole(Long roleId) {
        return toResponse(requireRole(roleId));
    }

    @Override
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String code = normalizeCode(request.code());
        if (roleMapper.selectByCode(code) != null) {
            throw BusinessException.conflict("角色编码已存在: " + code);
        }
        permissionService.assertPermissionsExist(request.permissionIds());

        Role role = new Role();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setSort(request.sort() == null ? 0 : request.sort());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setBuiltin(false);
        roleMapper.insert(role);

        if (request.permissionIds() != null) {
            replacePermissions(role.getId(), request.permissionIds());
        }
        log.info("Role created: {} ({})", role.getCode(), role.getId());
        return getRole(role.getId());
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long roleId, RoleRequest request) {
        Role role = requireRole(roleId);
        String code = normalizeCode(request.code());

        boolean builtin = Boolean.TRUE.equals(role.getBuiltin());
        if (builtin && !role.getCode().equals(code)) {
            throw BusinessException.badRequest("内置角色编码不可修改");
        }
        if (!role.getCode().equals(code)) {
            Role existing = roleMapper.selectByCode(code);
            if (existing != null && !existing.getId().equals(roleId)) {
                throw BusinessException.conflict("角色编码已存在: " + code);
            }
        }

        boolean permissionsChanged = request.permissionIds() != null;
        if (permissionsChanged) {
            permissionService.assertPermissionsExist(request.permissionIds());
        }

        boolean codeChanged = !role.getCode().equals(code);
        boolean disabled = request.status() != null && request.status() == 0
                && !Objects.equals(role.getStatus(), 0);

        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        if (request.sort() != null) {
            role.setSort(request.sort());
        }
        if (request.status() != null) {
            role.setStatus(request.status());
        }
        roleMapper.updateById(role);

        if (permissionsChanged) {
            replacePermissions(roleId, request.permissionIds());
        }
        if (codeChanged || permissionsChanged || disabled) {
            revokeUsersOfRole(roleId);
        }
        log.info("Role updated: {} ({})", role.getCode(), roleId);
        return getRole(roleId);
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        Role role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw BusinessException.conflict("内置角色不允许删除");
        }
        long users = countUsersByRoleId(roleId);
        if (users > 0) {
            throw BusinessException.conflict("该角色下仍有 " + users + " 个用户，无法删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        log.info("Role deleted: {} ({})", role.getCode(), roleId);
    }

    @Override
    @Transactional
    public RoleResponse assignPermissions(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        permissionService.assertPermissionsExist(permissionIds);
        replacePermissions(roleId, permissionIds);
        revokeUsersOfRole(roleId);
        log.info("Permissions changed for role {}", roleId);
        return getRole(roleId);
    }

    @Override
    public List<Role> getRolesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return roleMapper.selectEnabledByUserId(userId);
    }

    @Override
    public List<RoleSummary> getRoleSummaries(Long userId) {
        return getRolesByUserId(userId).stream()
                .map(role -> new RoleSummary(String.valueOf(role.getId()), role.getCode(), role.getName()))
                .toList();
    }

    @Override
    public void assertRolesExist(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = roleIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return;
        }
        List<Role> found = roleMapper.selectByIds(uniqueIds);
        if (found.size() != uniqueIds.size()) {
            Set<Long> foundIds = found.stream().map(Role::getId).collect(Collectors.toSet());
            String missing = uniqueIds.stream().filter(id -> !foundIds.contains(id))
                    .map(String::valueOf).collect(Collectors.joining(", "));
            throw BusinessException.badRequest("角色不存在: " + missing);
        }
    }

    @Override
    public Role getRoleByCode(String code) {
        return roleMapper.selectByCode(normalizeCode(code));
    }

    @Override
    public long countUsersByRoleId(Long roleId) {
        return roleMapper.countUsersByRoleId(roleId);
    }

    @Override
    public long countEnabledUsersByRoleId(Long roleId) {
        return roleMapper.countEnabledUsersByRoleId(roleId);
    }

    private Role requireRole(Long roleId) {
        Role role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }
        return role;
    }

    private void replacePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        if (permissionIds == null) {
            return;
        }
        permissionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(permissionId -> rolePermissionMapper.insert(new RolePermission(roleId, permissionId)));
    }

    private void revokeUsersOfRole(Long roleId) {
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(roleId);
        userIds.forEach(tokenService::revokeAll);
    }

    private RoleResponse toResponse(Role role) {
        List<String> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleId(role.getId()).stream()
                .map(String::valueOf)
                .toList();
        return new RoleResponse(
                String.valueOf(role.getId()),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getSort(),
                role.getStatus(),
                role.getBuiltin(),
                permissionIds,
                countUsersByRoleId(role.getId()),
                role.getCreateTime() == null ? null : role.getCreateTime().toString()
        );
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
