package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.rag.RagSettingResponse;
import bao.gabriel.cognia.system.dto.rag.UpdateRagSettingRequest;
import bao.gabriel.cognia.system.entity.RagSetting;

public interface RagSettingService {

    RagSetting getSetting();

    /**
     * Platform-wide switch: when false no chat request may use web search tools.
     */
    boolean webSearchEnabled();

    /**
     * When false the per-request user toggle is ignored and web search stays on
     * (subject to {@link #webSearchEnabled()} and the selected channels).
     */
    boolean allowUserWebSearch();

    RagSettingResponse getAdminSettings();

    RagSettingResponse updateSettings(UpdateRagSettingRequest request, Long operatorId);
}
