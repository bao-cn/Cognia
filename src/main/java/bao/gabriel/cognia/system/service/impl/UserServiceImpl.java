package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.common.util.ReservedNames;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.security.TokenService;
import bao.gabriel.cognia.system.dto.user.ProfileUpdateRequest;
import bao.gabriel.cognia.system.dto.user.RoleSummary;
import bao.gabriel.cognia.system.dto.user.UserCreateRequest;
import bao.gabriel.cognia.system.dto.user.UserResponse;
import bao.gabriel.cognia.system.dto.user.UserUpdateRequest;
import bao.gabriel.cognia.system.entity.Role;
import bao.gabriel.cognia.system.entity.Team;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.entity.UserConsent;
import bao.gabriel.cognia.system.entity.UserRole;
import bao.gabriel.cognia.system.mapper.UserConsentMapper;
import bao.gabriel.cognia.system.mapper.TeamMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.mapper.UserRoleMapper;
import bao.gabriel.cognia.system.service.MinioStorageService;
import bao.gabriel.cognia.system.service.PermissionService;
import bao.gabriel.cognia.system.service.RoleService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String DEFAULT_ROLE_CODE = "USER";

    private final TeamMapper teamMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserConsentMapper userConsentMapper;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final SiteSettingService settingService;
    private final MinioStorageService minioStorageService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User findByAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return null;
        }
        String trimmed = account.trim();
        String normalizedEmail = trimmed.toLowerCase(Locale.ROOT);
        return baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUsername, trimmed).or().eq(User::getEmail, normalizedEmail))
                .last("LIMIT 1"));
    }

    @Override
    public User requireUser(Long userId) {
        User user = userId == null ? null : getById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    @Override
    public LoginUser buildLoginUser(User user) {
        Set<String> permissions = permissionService.getPermissionCodesByUserId(user.getId());
        List<String> roles = roleService.getRolesByUserId(user.getId()).stream()
                .map(Role::getCode)
                .toList();
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .roles(roles)
                .permissions(List.copyOf(permissions))
                .build();
    }

    @Override
    public UserResponse getProfile(Long userId) {
        User user = requireUser(userId);
        return toResponse(user, true);
    }

    @Override
    public PageResult<UserResponse> pageUsers(long page, long size, String keyword, Integer status, Long roleId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, trimmed)
                    .or().like(User::getNickname, trimmed)
                    .or().like(User::getEmail, trimmed));
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (roleId != null) {
            wrapper.apply("EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = sys_user.id AND ur.role_id = {0})",
                    roleId);
        }
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);

        Page<User> result = page(new Page<>(page, size), wrapper);
        List<UserResponse> records = result.getRecords().stream()
                .map(user -> toResponse(user, false))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        assertUsernameAvailable(username, null);
        assertEmailAvailable(email, null);

        List<Long> roleIds = request.roleIds();
        if (roleIds == null) {
            Role defaultRole = roleService.getRoleByCode(DEFAULT_ROLE_CODE);
            roleIds = defaultRole == null ? List.of() : List.of(defaultRole.getId());
        }
        roleService.assertRolesExist(roleIds);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username);
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setRemark(null);
        save(user);

        replaceRoles(user.getId(), roleIds);
        log.info("User created: {} ({})", user.getUsername(), user.getId());
        return toResponse(user, true);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request, Long operatorId) {
        User user = requireUser(userId);

        String email = null;
        if (request.email() != null) {
            if (!StringUtils.hasText(request.email())) {
                throw BusinessException.badRequest("邮箱不能为空");
            }
            email = normalizeEmail(request.email());
            assertEmailAvailable(email, userId);
        }

        boolean disabling = request.status() != null && request.status() == 0 && user.isEnabled();
        if (disabling) {
            if (userId.equals(operatorId)) {
                throw BusinessException.badRequest("不能禁用当前登录账户");
            }
            assertNotLastEnabledAdmin(user);
        }

        if (request.nickname() != null) {
            user.setNickname(request.nickname().trim());
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.remark() != null) {
            user.setRemark(request.remark());
        }
        updateById(user);

        if (disabling) {
            tokenService.revokeAll(userId);
        }

        log.info("User updated: {} ({})", user.getUsername(), userId);
        return toResponse(requireUser(userId), true);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long operatorId) {
        User user = requireUser(userId);
        if (userId.equals(operatorId)) {
            throw BusinessException.badRequest("不能删除当前登录账户");
        }
        assertNotLastEnabledAdmin(user);
        Long ownedTeams = teamMapper.selectCount(new LambdaQueryWrapper<Team>()
                .eq(Team::getOwnerId, userId)
                .eq(Team::getIsDeleted, false));
        if (ownedTeams != null && ownedTeams > 0) {
            throw BusinessException.conflict("你仍是 " + ownedTeams + " 个团队的创建者，请先转移所有权或解散团队后再注销账户");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        userConsentMapper.delete(new LambdaQueryWrapper<UserConsent>().eq(UserConsent::getUserId, userId));
        removeById(userId);
        tokenService.revokeAll(userId);
        log.info("User deleted: {} ({})", user.getUsername(), userId);
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, Integer status, Long operatorId) {
        User user = requireUser(userId);
        if (status == 0) {
            if (userId.equals(operatorId)) {
                throw BusinessException.badRequest("不能禁用当前登录账户");
            }
            if (user.isEnabled()) {
                assertNotLastEnabledAdmin(user);
            }
        }
        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        updateById(update);

        if (status == 0) {
            tokenService.revokeAll(userId);
        }
        log.info("User status changed: {} -> {}", userId, status);
    }

    @Override
    @Transactional
    public UserResponse assignRoles(Long userId, List<Long> roleIds, Long operatorId) {
        User user = requireUser(userId);
        roleService.assertRolesExist(roleIds);
        if (!containsRole(roleIds, ADMIN_ROLE_CODE)) {
            assertNotLastEnabledAdmin(user);
        }
        replaceRoles(userId, roleIds);
        tokenService.revokeAll(userId);
        log.info("Roles of user {} replaced by operator {}", userId, operatorId);
        return toResponse(requireUser(userId), true);
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        requireUser(userId);
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        updateById(update);
        tokenService.revokeAll(userId);
        log.info("Password reset for user {}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw BusinessException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        updateById(update);
        tokenService.revokeAll(userId);
        log.info("Password changed for user {}", userId);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = requireUser(userId);
        boolean changed = false;
        if (request.nickname() != null) {
            user.setNickname(request.nickname().trim());
            changed = true;
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
            changed = true;
        }
        if (changed) {
            updateById(user);
        }
        return toResponse(requireUser(userId), true);
    }

    @Override
    public UserResponse updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file) {
        User user = requireUser(userId);
        String url = minioStorageService.uploadAvatar(userId, file).url();
        user.setAvatar(url);
        updateById(user);
        return toResponse(requireUser(userId), true);
    }

    @Override
    public void updateLastLogin(Long userId, String ip) {
        User update = new User();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(ip);
        updateById(update);
    }

    @Override
    public void assertUsernameAvailable(String username, Long excludeUserId) {
        String value = username == null ? "" : username.trim();
        if (ReservedNames.isReserved(value)) {
            throw BusinessException.badRequest("用户名「" + value + "」是系统保留字，请更换");
        }
        long count = baseMapper.countByUsernameIncludingDeleted(value);
        if (count > 0) {
            throw BusinessException.conflict("用户名已被占用（含已注销待清理账户）");
        }
        // Users and teams share the one-segment route namespace.
        Long teamCount = teamMapper.countBySlugIncludingDeleted(value.toLowerCase(java.util.Locale.ROOT));
        if (teamCount != null && teamCount > 0) {
            throw BusinessException.conflict("该标识已被团队使用");
        }
    }

    @Override
    public void assertEmailAvailable(String email, Long excludeUserId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, normalizeEmail(email))
                .ne(excludeUserId != null, User::getId, excludeUserId));
        if (count != null && count > 0) {
            throw BusinessException.conflict("邮箱已被注册");
        }
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(roleId -> userRoleMapper.insert(new UserRole(userId, roleId)));
    }

    /**
     * Guards the platform against losing every enabled ADMIN account.
     */
    private void assertNotLastEnabledAdmin(User user) {
        if (user == null || !user.isEnabled()) {
            return;
        }
        boolean isAdmin = roleService.getRolesByUserId(user.getId()).stream()
                .anyMatch(role -> ADMIN_ROLE_CODE.equalsIgnoreCase(role.getCode()));
        if (!isAdmin) {
            return;
        }
        Role adminRole = roleService.getRoleByCode(ADMIN_ROLE_CODE);
        if (adminRole != null && roleService.countEnabledUsersByRoleId(adminRole.getId()) <= 1) {
            throw BusinessException.conflict("系统至少需要保留一个启用的管理员（ADMIN）");
        }
    }

    private boolean containsRole(List<Long> roleIds, String roleCode) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        Role role = roleService.getRoleByCode(roleCode);
        return role != null && roleIds.contains(role.getId());
    }

    private UserResponse toResponse(User user, boolean withPermissions) {
        List<RoleSummary> roles = roleService.getRoleSummaries(user.getId());
        List<String> permissions = null;
        if (withPermissions) {
            permissions = List.copyOf(permissionService.getPermissionCodesByUserId(user.getId()));
        }
        return new UserResponse(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getStatus(),
                roles,
                permissions,
                format(user.getLastLoginTime()),
                user.getLastLoginIp(),
                format(user.getCreateTime()),
                user.getRemark()
        );
    }

    private String format(LocalDateTime dateTime) {
        return settingService.formatDateTime(dateTime);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
