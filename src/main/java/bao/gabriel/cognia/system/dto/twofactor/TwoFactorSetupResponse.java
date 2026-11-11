package bao.gabriel.cognia.system.dto.twofactor;

public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
