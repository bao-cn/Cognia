package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.security.TotpService;
import bao.gabriel.cognia.system.dto.twofactor.TwoFactorSetupResponse;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final UserMapper userMapper;
    private final TotpService totpService;
    private final SecurityCryptoService cryptoService;

    public TwoFactorSetupResponse setup(User user) {
        String secret = totpService.generateSecret();
        user.setTwoFactorSecret(cryptoService.encrypt(secret));
        user.setTwoFactorEnabled(false);
        userMapper.updateById(user);
        return new TwoFactorSetupResponse(secret, totpService.otpauthUri(user.getUsername(), secret));
    }

    public void enable(User user, String code) {
        if (!StringUtils.hasText(user.getTwoFactorSecret())) {
            throw BusinessException.badRequest("请先生成两步验证密钥");
        }
        if (!verify(user, code)) {
            throw BusinessException.badRequest("验证码不正确");
        }
        user.setTwoFactorEnabled(true);
        userMapper.updateById(user);
    }

    public void disable(User user, String code) {
        require(user, code);
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userMapper.updateById(user);
    }

    public boolean verify(User user, String code) {
        String secret = cryptoService.decrypt(user.getTwoFactorSecret());
        return totpService.verify(secret, code);
    }

    public void require(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return;
        }
        if (!StringUtils.hasText(code)) {
            throw BusinessException.badRequest("该操作需要两步验证码");
        }
        if (!verify(user, code)) {
            throw BusinessException.badRequest("两步验证码不正确");
        }
    }
}
