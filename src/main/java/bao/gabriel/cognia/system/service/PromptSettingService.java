package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.prompt.PromptSettingResponse;
import bao.gabriel.cognia.system.dto.prompt.UpdatePromptSettingRequest;
import bao.gabriel.cognia.system.entity.PromptSetting;

public interface PromptSettingService {

    PromptSetting getSetting();

    PromptSettingResponse getAdminSettings();

    PromptSettingResponse getDefaults();

    PromptSettingResponse updateSettings(UpdatePromptSettingRequest request, Long operatorId);

    PromptSettingResponse reset(Long operatorId);
}
