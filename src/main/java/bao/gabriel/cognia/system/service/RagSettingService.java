package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.rag.RagSettingResponse;
import bao.gabriel.cognia.system.dto.rag.UpdateRagSettingRequest;
import bao.gabriel.cognia.system.entity.RagSetting;

public interface RagSettingService {

    RagSetting getSetting();

    RagSettingResponse getAdminSettings();

    RagSettingResponse updateSettings(UpdateRagSettingRequest request, Long operatorId);
}
