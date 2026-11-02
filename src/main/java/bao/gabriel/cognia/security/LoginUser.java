package bao.gabriel.cognia.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal stored in the SecurityContext. Roles are exposed as
 * {@code ROLE_XXX} authorities, permissions keep their raw code
 * (for example {@code user:read}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String avatar;

    /**
     * JWT id of the current access token, needed to revoke the session.
     */
    private String jti;

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        if (permissions != null) {
            permissions.stream()
                    .filter(permission -> permission != null && !permission.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        return authorities;
    }

    public boolean hasRole(String role) {
        return roles != null && roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
