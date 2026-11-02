package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.settings.MailTemplatePreviewResponse;
import bao.gabriel.cognia.system.dto.settings.MailTemplateResponse;
import bao.gabriel.cognia.system.dto.settings.UpdateMailTemplateRequest;

import java.util.List;
import java.util.Map;

public interface MailTemplateService {

    String REGISTER_CODE = "REGISTER_CODE";
    String SMTP_TEST = "SMTP_TEST";

    List<MailTemplateResponse> listTemplates();

    MailTemplateResponse updateTemplate(String code, UpdateMailTemplateRequest request, Long operatorId);

    MailTemplateResponse resetTemplate(String code, Long operatorId);

    MailTemplatePreviewResponse previewTemplate(String code, UpdateMailTemplateRequest request);

    RenderedMail render(String code, Map<String, String> variables);
}
