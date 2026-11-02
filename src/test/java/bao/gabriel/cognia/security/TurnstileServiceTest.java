package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.settings.TurnstileConfig;
import bao.gabriel.cognia.system.service.SiteSettingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnstileServiceTest {

    private final SiteSettingService settingService = mock(SiteSettingService.class);
    private final TurnstileService turnstileService = new TurnstileService(settingService);

    @Test
    void isNoOpWhenTheFlowDoesNotRequireTurnstile() {
        assertDoesNotThrow(() -> turnstileService.verify(null, "127.0.0.1", false));
    }

    @Test
    void requiresATokenWhenRequired() {
        when(settingService.getTurnstileConfig())
                .thenReturn(new TurnstileConfig(true, "site-key", "secret-key"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> turnstileService.verify("  ", "127.0.0.1", true));
        assertEquals("请先完成人机验证", exception.getMessage());
    }

    @Test
    void failsFastWhenTheKeysAreMissing() {
        when(settingService.getTurnstileConfig())
                .thenReturn(new TurnstileConfig(false, null, null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> turnstileService.verify("token", "127.0.0.1", true));
        assertEquals("人机验证尚未配置，请联系管理员", exception.getMessage());
    }
}
