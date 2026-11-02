package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.settings.UpdateMailTemplateRequest;
import bao.gabriel.cognia.system.entity.MailTemplate;
import bao.gabriel.cognia.system.mapper.MailTemplateMapper;
import bao.gabriel.cognia.system.service.EmailHtmlRenderer;
import bao.gabriel.cognia.system.service.MailTemplateService;
import bao.gabriel.cognia.system.service.RenderedMail;
import bao.gabriel.cognia.system.service.SiteSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailTemplateServiceImplTest {

    private final MailTemplateMapper mapper = mock(MailTemplateMapper.class);
    private final SiteSettingService settingService = mock(SiteSettingService.class);
    private final MailTemplateServiceImpl service = new MailTemplateServiceImpl(
            mapper, settingService, new EmailHtmlRenderer());

    @Test
    void renderUsesTheStoredTemplateAndVariables() {
        MailTemplate template = new MailTemplate();
        template.setCode(MailTemplateService.REGISTER_CODE);
        template.setSubject("【{{siteName}}】验证码");
        template.setContent("您的验证码是 {{code}}");
        when(mapper.selectOne(any())).thenReturn(template);

        RenderedMail mail = service.render(MailTemplateService.REGISTER_CODE,
                Map.of("siteName", "Cognia", "code", "654321"));

        assertEquals("【Cognia】验证码", mail.subject());
        assertEquals("您的验证码是 654321", mail.content());
    }

    @Test
    void rejectsUnknownPlaceholdersBeforeSaving() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateTemplate(MailTemplateService.REGISTER_CODE,
                        new UpdateMailTemplateRequest("主题 {{unknown}}", "内容"), 1L));

        assertEquals("不支持模板变量: unknown；可用变量: siteName, email, code, ttlMinutes, expiresAt, year",
                exception.getMessage());
    }
}
