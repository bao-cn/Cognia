package bao.gabriel.cognia.system.dto.auth;

public record LoginOptionsResponse(boolean hasPassword, boolean twoFactorEnabled) {
}
