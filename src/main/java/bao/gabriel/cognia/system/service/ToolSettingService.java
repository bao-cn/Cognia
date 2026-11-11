package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.tool.ToolSettingResponse;
import bao.gabriel.cognia.system.dto.tool.UpdateToolSettingRequest;
import bao.gabriel.cognia.system.entity.ToolSetting;

public interface ToolSettingService {

    ToolSetting getSetting();

    ToolSettingResponse getAdminSettings();

    ToolSettingResponse updateSettings(UpdateToolSettingRequest request, Long operatorId);

    /** Whether web_search has an endpoint configured and is switched on. */
    boolean webSearchUsable();

    boolean webFetchEnabled();
}
