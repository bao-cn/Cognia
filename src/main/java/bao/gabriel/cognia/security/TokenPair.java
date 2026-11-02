package bao.gabriel.cognia.security;

/**
 * Token pair returned by login and refresh.
 */
public record TokenPair(String accessToken, String refreshToken, String tokenType,
                        long expiresIn, long refreshExpiresIn) {
}
