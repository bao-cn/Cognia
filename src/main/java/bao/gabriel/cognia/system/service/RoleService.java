package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.role.RoleRequest;
import bao.gabriel.cognia.system.dto.role.RoleResponse;
import bao.gabriel.cognia.system.dto.user.RoleSummary;
import bao.gabriel.cognia.system.entity.Role;

import java.util.Collection;
import java.util.List;

public interface RoleService {

    List<RoleResponse> listRoles(String keyword);

    RoleResponse getRole(Long roleId);

    RoleResponse createRole(RoleRequest request);

    RoleResponse updateRole(Long roleId, RoleRequest request);

    void deleteRole(Long roleId);

    RoleResponse assignPermissions(Long roleId, List<Long> permissionIds);

    List<Role> getRolesByUserId(Long userId);

    List<RoleSummary> getRoleSummaries(Long userId);

    void assertRolesExist(Collection<Long> roleIds);

    Role getRoleByCode(String code);

    long countUsersByRoleId(Long roleId);

    long countEnabledUsersByRoleId(Long roleId);
}
