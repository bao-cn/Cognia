package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.api.ResultCode;
import bao.gabriel.cognia.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Convenience accessors for the authenticated principal.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getLoginUser() {
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    public static boolean isAuthenticated() {
        return getLoginUserOrNull() != null;
    }

    public static boolean hasRole(String role) {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser != null && loginUser.hasRole(role);
    }

    public static boolean hasPermission(String permission) {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser != null && loginUser.hasPermission(permission);
    }
}
