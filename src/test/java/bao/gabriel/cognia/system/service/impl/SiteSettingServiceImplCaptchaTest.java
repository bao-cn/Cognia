package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.settings.CaptchaConfig;
import bao.gabriel.cognia.system.dto.settings.CaptchaProvider;
import bao.gabriel.cognia.system.entity.SiteSetting;
import bao.gabriel.cognia.system.mapper.SiteSettingMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SiteSettingServiceImplCaptchaTest {

    private static final long SINGLETON_ID = SiteSetting.SINGLETON_ID;

    private final SiteSettingMapper settingMapper = mock(SiteSettingMapper.class);
    private final SecurityCryptoService cryptoService = mock(SecurityCryptoService.class);
    private final SiteSettingServiceImpl service =
            new SiteSettingServiceImpl(settingMapper, cryptoService);

    @Test
    void infersTurnstileForLegacyRowsWithoutAProvider() {
        SiteSetting setting = setting();
        setting.setTurnstileSiteKey("turnstile-site");
        setting.setTurnstileSecretKey("encrypted-secret");
        when(settingMapper.selectById(SINGLETON_ID)).thenReturn(setting);
        when(cryptoService.decrypt("encrypted-secret")).thenReturn("turnstile-secret");

        CaptchaConfig config = service.getCaptchaConfig();

        assertEquals(CaptchaProvider.TURNSTILE, config.provider());
        assertEquals("turnstile-secret", config.turnstileSecretKey());
    }

    @Test
    void keepsAnExplicitNoneEvenWhenOldKeysRemain() {
        SiteSetting setting = setting();
        setting.setCaptchaProvider("NONE");
        setting.setTurnstileSiteKey("turnstile-site");
        setting.setTurnstileSecretKey("encrypted-secret");
        when(settingMapper.selectById(SINGLETON_ID)).thenReturn(setting);

        assertEquals(CaptchaProvider.NONE, service.getCaptchaConfig().provider());
    }

    @Test
    void reportsNoneWhenTheSelectedProviderIsNotFullyConfigured() {
        SiteSetting setting = setting();
        setting.setCaptchaProvider("GEETEST");
        setting.setGeetestCaptchaId("captcha-id");
        when(settingMapper.selectById(SINGLETON_ID)).thenReturn(setting);

        assertEquals(CaptchaProvider.NONE, service.getCaptchaConfig().provider());
    }

    @Test
    void resolvesGeetestWhenBothCredentialsArePresent() {
        SiteSetting setting = setting();
        setting.setCaptchaProvider("GEETEST");
        setting.setGeetestCaptchaId("captcha-id");
        setting.setGeetestCaptchaKey("encrypted-key");
        when(settingMapper.selectById(SINGLETON_ID)).thenReturn(setting);
        when(cryptoService.decrypt("encrypted-key")).thenReturn("captcha-key");

        CaptchaConfig config = service.getCaptchaConfig();

        assertEquals(CaptchaProvider.GEETEST, config.provider());
        assertEquals("captcha-id", config.geetestCaptchaId());
        assertEquals("captcha-key", config.geetestCaptchaKey());
    }

    @Test
    void ignoresUnknownProviderValues() {
        SiteSetting setting = setting();
        setting.setCaptchaProvider("UNKNOWN");
        setting.setTurnstileSiteKey("turnstile-site");
        setting.setTurnstileSecretKey("encrypted-secret");
        when(settingMapper.selectById(SINGLETON_ID)).thenReturn(setting);
        when(cryptoService.decrypt("encrypted-secret")).thenReturn("turnstile-secret");

        // Falls back to the legacy inference instead of failing the flow.
        assertEquals(CaptchaProvider.TURNSTILE, service.getCaptchaConfig().provider());
    }

    private SiteSetting setting() {
        SiteSetting setting = new SiteSetting();
        setting.setId(SINGLETON_ID);
        setting.setSiteName("Cognia");
        return setting;
    }
}
