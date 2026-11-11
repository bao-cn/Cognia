package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.config.properties.InitProperties;
import bao.gabriel.cognia.system.entity.Permission;
import bao.gabriel.cognia.system.entity.Role;
import bao.gabriel.cognia.system.entity.RolePermission;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.entity.UserRole;
import bao.gabriel.cognia.system.mapper.PermissionMapper;
import bao.gabriel.cognia.system.mapper.RoleMapper;
import bao.gabriel.cognia.system.mapper.RolePermissionMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Idempotent bootstrap of the RBAC catalogue and the initial administrator.
 * Permission codes are the contract between the backend annotations and the
 * frontend permission checks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.init", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    public static final String ADMIN_ROLE = "ADMIN";
    public static final String USER_ROLE = "USER";

    private static final List<PermissionSeed> PERMISSIONS = List.of(
            new PermissionSeed("console:access", "访问管理控制台", "MENU", "/console", null, 10),
            new PermissionSeed("user:read", "查看用户", "API", "/api/v1/users", "GET", 20),
            new PermissionSeed("user:create", "创建用户", "API", "/api/v1/users", "POST", 21),
            new PermissionSeed("user:update", "编辑用户", "API", "/api/v1/users/{id}", "PUT", 22),
            new PermissionSeed("user:delete", "删除用户", "API", "/api/v1/users/{id}", "DELETE", 23),
            new PermissionSeed("user:assign-role", "分配用户角色", "API", "/api/v1/users/{id}/roles", "PUT", 24),
            new PermissionSeed("user:reset-password", "重置用户密码", "API", "/api/v1/users/{id}/password", "PUT", 25),
            new PermissionSeed("role:read", "查看角色", "API", "/api/v1/roles", "GET", 30),
            new PermissionSeed("role:create", "创建角色", "API", "/api/v1/roles", "POST", 31),
            new PermissionSeed("role:update", "编辑角色", "API", "/api/v1/roles/{id}", "PUT", 32),
            new PermissionSeed("role:delete", "删除角色", "API", "/api/v1/roles/{id}", "DELETE", 33),
            new PermissionSeed("role:assign-permission", "分配角色权限", "API", "/api/v1/roles/{id}/permissions", "PUT", 34),
            new PermissionSeed("permission:read", "查看权限", "API", "/api/v1/permissions", "GET", 40),
            new PermissionSeed("settings:read", "查看系统设置", "API", "/api/v1/settings", "GET", 50),
            new PermissionSeed("settings:update", "修改系统设置", "API", "/api/v1/settings", "PUT", 51),
            new PermissionSeed("storage:read", "查看对象存储设置", "API", "/api/v1/storage", "GET", 52),
            new PermissionSeed("storage:update", "修改对象存储设置", "API", "/api/v1/storage", "PUT", 53),
            new PermissionSeed("audit:read", "查看审计日志", "API", "/api/v1/audit-logs", "GET", 60),
            new PermissionSeed("task:read", "查看计划任务", "API", "/api/v1/scheduled-tasks", "GET", 61),
            new PermissionSeed("task:update", "修改计划任务", "API", "/api/v1/scheduled-tasks", "PUT", 62),
                new PermissionSeed("dashboard:read", "查看运营看板", "API", "/api/v1/admin/dashboard", "GET", 65),
            new PermissionSeed("team:read", "查看团队", "API", "/api/v1/admin/teams", "GET", 63),
            new PermissionSeed("team:update", "管理团队", "API", "/api/v1/admin/teams", "DELETE", 64),
            new PermissionSeed("model:read", "查看模型配置", "API", "/api/v1/admin/models", "GET", 65),
            new PermissionSeed("model:update", "修改模型配置", "API", "/api/v1/admin/models", "PUT", 66),
            new PermissionSeed("rag:read", "查看检索参数", "API", "/api/v1/admin/rag", "GET", 67),
            new PermissionSeed("rag:update", "修改检索参数", "API", "/api/v1/admin/rag", "PUT", 68),
            new PermissionSeed("discover:read", "查看发现广场", "API", "/api/v1/discover", "GET", 69),
            new PermissionSeed("discover:update", "管理精选频道", "API", "/api/v1/discover", "PUT", 70),
            new PermissionSeed("tool:read", "查看工具配置", "API", "/api/v1/admin/tools", "GET", 71),
            new PermissionSeed("tool:update", "修改工具配置", "API", "/api/v1/admin/tools", "PUT", 72),
            new PermissionSeed("trace:read", "查看开发链路", "API", "/api/v1/admin/traces", "GET", 73),
            new PermissionSeed("trace:write", "清理开发链路", "API", "/api/v1/admin/traces", "DELETE", 74)
    );

    private final InitProperties initProperties;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Initializing RBAC data...");

        List<Permission> permissions = syncPermissions();
        Role adminRole = ensureRole(ADMIN_ROLE, "超级管理员", "拥有平台全部权限", 0);
        ensureRole(USER_ROLE, "普通用户", "可访问个人空间与对话功能", 10);

        syncAdminPermissions(adminRole, permissions);
        ensureAdminUser(adminRole);
    }

    private List<Permission> syncPermissions() {
        for (PermissionSeed seed : PERMISSIONS) {
            Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                    .eq(Permission::getCode, seed.code()));
            if (permission == null) {
                permission = new Permission();
                permission.setCode(seed.code());
                permission.setName(seed.name());
                permission.setType(seed.type());
                permission.setResource(seed.resource());
                permission.setMethod(seed.method());
                permission.setSort(seed.sort());
                permissionMapper.insert(permission);
            } else {
                boolean changed = !Objects.equals(permission.getName(), seed.name())
                        || !Objects.equals(permission.getType(), seed.type())
                        || !Objects.equals(permission.getResource(), seed.resource())
                        || !Objects.equals(permission.getMethod(), seed.method())
                        || !Objects.equals(permission.getSort(), seed.sort());
                if (changed) {
                    permission.setName(seed.name());
                    permission.setType(seed.type());
                    permission.setResource(seed.resource());
                    permission.setMethod(seed.method());
                    permission.setSort(seed.sort());
                    permissionMapper.updateById(permission);
                }
            }
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getSort));
    }

    private Role ensureRole(String code, String name, String description, int sort) {
        Role role = roleMapper.selectByCode(code);
        if (role == null) {
            role = new Role();
            role.setCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setSort(sort);
            role.setStatus(1);
            role.setBuiltin(true);
            roleMapper.insert(role);
            log.info("Created built-in role {}", code);
        } else if (!Boolean.TRUE.equals(role.getBuiltin())) {
            role.setBuiltin(true);
            roleMapper.updateById(role);
        }
        return role;
    }

    private void syncAdminPermissions(Role adminRole, List<Permission> permissions) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, adminRole.getId()));
        for (Permission permission : permissions) {
            rolePermissionMapper.insert(new RolePermission(adminRole.getId(), permission.getId()));
        }
        log.info("ADMIN role granted {} permissions", permissions.size());
    }

    private void ensureAdminUser(Role adminRole) {
        String username = initProperties.getAdminUsername();
        String email = initProperties.getAdminEmail() == null
                ? "admin@cognia.local"
                : initProperties.getAdminEmail().trim().toLowerCase(Locale.ROOT);

        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUsername, username).or().eq(User::getEmail, email))
                .last("LIMIT 1"));

        if (admin == null) {
            admin = new User();
            admin.setUsername(username);
            admin.setPassword(passwordEncoder.encode(initProperties.getAdminPassword()));
            admin.setNickname("系统管理员");
            admin.setEmail(email);
            admin.setStatus(1);
            admin.setRemark("系统初始化创建");
            userMapper.insert(admin);
            log.warn("""

                    ============================================================
                     Initial administrator created
                     username: {}
                     password: {}   <-- change it after the first login!
                    ============================================================
                    """, username, initProperties.getAdminPassword());
        }

        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(admin.getId());
        if (!roleIds.contains(adminRole.getId())) {
            userRoleMapper.insert(new UserRole(admin.getId(), adminRole.getId()));
            log.info("ADMIN role assigned to user {}", admin.getUsername());
        }
    }

    private record PermissionSeed(String code, String name, String type,
                                  String resource, String method, int sort) {
    }
}
