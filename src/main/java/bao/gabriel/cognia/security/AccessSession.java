package bao.gabriel.cognia.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot persisted in Redis for an issued access token. It allows the JWT
 * filter to authenticate without touching PostgreSQL on every request and
 * makes immediate logout / permission revocation possible.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessSession implements Serializable {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String avatar;

    private String jti;
    private String refreshJti;

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    private long loginTime;
    private String ip;
    private String userAgent;
}
