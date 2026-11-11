package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.security.LoginUser;
import bao.gabriel.cognia.system.dto.user.ProfileUpdateRequest;
import bao.gabriel.cognia.system.dto.user.UserCreateRequest;
import bao.gabriel.cognia.system.dto.user.UserResponse;
import bao.gabriel.cognia.system.dto.user.UserUpdateRequest;
import bao.gabriel.cognia.system.entity.User;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

public interface UserService extends IService<User> {

    User findByAccount(String account);

    User requireUser(Long userId);

    LoginUser buildLoginUser(User user);

    UserResponse getProfile(Long userId);

    PageResult<UserResponse> pageUsers(long page, long size, String keyword, Integer status, Long roleId);

    UserResponse createUser(UserCreateRequest request);

    UserResponse updateUser(Long userId, UserUpdateRequest request, Long operatorId);

    void deleteUser(Long userId, Long operatorId);

    void updateStatus(Long userId, Integer status, Long operatorId);

    UserResponse assignRoles(Long userId, List<Long> roleIds, Long operatorId);

    void resetPassword(Long userId, String newPassword);

    void changePassword(Long userId, String oldPassword, String newPassword);

    UserResponse updateProfile(Long userId, ProfileUpdateRequest request);

    UserResponse updatePreferences(Long userId, boolean autoWebSearch);

    void setPassword(Long userId, String newPassword);

    void deleteSelf(Long userId);

    UserResponse updateEmail(Long userId, String email);

    boolean matchesPassword(Long userId, String rawPassword);

    UserResponse updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file);

    void updateLastLogin(Long userId, String ip);

    void assertUsernameAvailable(String username, Long excludeUserId);

    void assertEmailAvailable(String email, Long excludeUserId);
}
