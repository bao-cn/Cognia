package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.role.PermissionResponse;
import bao.gabriel.cognia.system.entity.Permission;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PermissionService {

    List<PermissionResponse> listPermissions();

    List<Permission> getPermissionsByUserId(Long userId);

    Set<String> getPermissionCodesByUserId(Long userId);

    void assertPermissionsExist(Collection<Long> permissionIds);
}
