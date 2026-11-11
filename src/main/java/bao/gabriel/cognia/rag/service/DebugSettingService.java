package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.system.dto.trace.DebugSettingResponse;
import bao.gabriel.cognia.system.dto.trace.UpdateDebugSettingRequest;

public interface DebugSettingService {
    boolean enabled();

    String hardcodedUsername();

    /**
     * Whether the given user's conversations should be traced.
     * Dev mode is global, but only the hardcoded administrator is recorded.
     */
    boolean shouldTrace(Long userId);

    DebugSettingResponse getSettings();

    DebugSettingResponse updateSettings(UpdateDebugSettingRequest request, Long operatorId);
}
