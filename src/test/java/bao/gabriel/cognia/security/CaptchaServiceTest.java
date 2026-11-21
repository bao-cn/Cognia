package bao.gabriel.cognia.security;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.settings.CaptchaConfig;
import bao.gabriel.cognia.system.dto.settings.CaptchaProvider;
import bao.gabriel.cognia.system.service.SiteSettingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {

    private final SiteSettingService settingService = mock(SiteSettingService.class);
    private final TurnstileVerifier turnstileVerifier = mock(TurnstileVerifier.class);
    private final GeeTestVerifier geeTestVerifier = mock(GeeTestVerifier.class);
    private final CaptchaService captchaService =
            new CaptchaService(settingService, turnstileVerifier, geeTestVerifier);

    @Test
    void isNoOpWhenTheFlowDoesNotRequireVerification() {
        assertDoesNotThrow(() -> captchaService.verify(null, null, "127.0.0.1", false));

        verifyNoInteractions(turnstileVerifier, geeTestVerifier);
    }

    @Test
    void failsFastWhenNoProviderIsConfigured() {
        when(settingService.getCaptchaConfig())
                .thenReturn(new CaptchaConfig(CaptchaProvider.NONE, null, null, null, null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> captchaService.verify(null, "token", "127.0.0.1", true));
        assertEquals("人机验证尚未配置，请联系管理员", exception.getMessage());
        verifyNoInteractions(turnstileVerifier, geeTestVerifier);
    }

    @Test
    void requiresATurnstileTokenWhenRequired() {
        when(settingService.getCaptchaConfig()).thenReturn(turnstileConfig());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> captchaService.verify(null, "  ", "127.0.0.1", true));
        assertEquals("请先完成人机验证", exception.getMessage());
        verifyNoInteractions(turnstileVerifier);
    }

    @Test
    void delegatesTheLegacyTurnstileToken() {
        when(settingService.getCaptchaConfig()).thenReturn(turnstileConfig());

        captchaService.verify(null, "legacy-token", "127.0.0.1", true);

        verify(turnstileVerifier).verify("legacy-token", "turnstile-secret", "127.0.0.1");
    }

    @Test
    void prefersTheStructuredSubmissionOverTheLegacyToken() {
        when(settingService.getCaptchaConfig()).thenReturn(turnstileConfig());

        captchaService.verify(new CaptchaSubmission("fresh-token", null, null, null, null),
                "legacy-token", "127.0.0.1", true);

        verify(turnstileVerifier).verify("fresh-token", "turnstile-secret", "127.0.0.1");
    }

    @Test
    void requiresAllGeeTestFields() {
        when(settingService.getCaptchaConfig()).thenReturn(geeTestConfig());

        CaptchaSubmission partial = new CaptchaSubmission(null, "lot", "output", null, "gen");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> captchaService.verify(partial, null, "127.0.0.1", true));
        assertEquals("请先完成人机验证", exception.getMessage());
        verifyNoInteractions(geeTestVerifier);
    }

    @Test
    void delegatesTheGeeTestSubmission() {
        when(settingService.getCaptchaConfig()).thenReturn(geeTestConfig());
        CaptchaSubmission submission = new CaptchaSubmission(null, "lot", "output", "pass", "gen");

        captchaService.verify(submission, null, "127.0.0.1", true);

        verify(geeTestVerifier).verify(submission, "geetest-id", "geetest-key");
    }

    private CaptchaConfig turnstileConfig() {
        return new CaptchaConfig(CaptchaProvider.TURNSTILE, "turnstile-site", "turnstile-secret", null, null);
    }

    private CaptchaConfig geeTestConfig() {
        return new CaptchaConfig(CaptchaProvider.GEETEST, null, null, "geetest-id", "geetest-key");
    }
}
