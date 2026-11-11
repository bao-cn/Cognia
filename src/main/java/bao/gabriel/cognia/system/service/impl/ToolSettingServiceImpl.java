package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.security.SecurityCryptoService;
import bao.gabriel.cognia.system.dto.tool.ToolSettingResponse;
import bao.gabriel.cognia.system.dto.tool.UpdateToolSettingRequest;
import bao.gabriel.cognia.system.entity.ToolSetting;
import bao.gabriel.cognia.system.mapper.ToolSettingMapper;
import bao.gabriel.cognia.system.service.SiteSettingService;
import bao.gabriel.cognia.system.service.ToolSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ToolSettingServiceImpl implements ToolSettingService {

    private static final long CACHE_TTL_MS = 10_000;

    private final ToolSettingMapper mapper;
    private final SecurityCryptoService cryptoService;
    private final SiteSettingService siteSettingService;

    private volatile ToolSetting cached;
    private volatile long cachedAt;

    @Override
    public ToolSetting getSetting() {
        ToolSetting local = cached;
        if (local != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return local;
        }
        ToolSetting setting = mapper.selectById(ToolSetting.SINGLETON_ID);
        if (setting == null) {
            setting = new ToolSetting();
            setting.setId(ToolSetting.SINGLETON_ID);
            setting.setWebSearchEnabled(true);
            setting.setWebFetchEnabled(true);
            setting.setMaxResults(5);
            setting.setUpdateTime(LocalDateTime.now());
            mapper.insert(setting);
        }
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return setting;
    }

    @Override
    public ToolSettingResponse getAdminSettings() {
        return toResponse(getSetting());
    }

    @Override
    @Transactional
    public ToolSettingResponse updateSettings(UpdateToolSettingRequest request, Long operatorId) {
        ToolSetting setting = getSetting();
        if (request.webSearchEnabled() != null) {
            setting.setWebSearchEnabled(request.webSearchEnabled());
        }
        if (request.webSearchEndpoint() != null) {
            String endpoint = request.webSearchEndpoint().trim();
            if (!endpoint.isEmpty() && !endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                throw BusinessException.badRequest("搜索接口地址必须以 http:// 或 https:// 开头");
            }
            setting.setWebSearchEndpoint(endpoint.isEmpty() ? null : endpoint);
        }
        if (StringUtils.hasText(request.webSearchApiKey())) {
            setting.setWebSearchApiKey(cryptoService.encrypt(request.webSearchApiKey().trim()));
        }
        if (Boolean.TRUE.equals(request.clearWebSearchApiKey())) {
            setting.setWebSearchApiKey(null);
        }
        if (request.webFetchEnabled() != null) {
            setting.setWebFetchEnabled(request.webFetchEnabled());
        }
        if (request.maxResults() != null) {
            setting.setMaxResults(request.maxResults());
        }
        setting.setUpdatedBy(operatorId);
        setting.setUpdateTime(LocalDateTime.now());
        mapper.updateById(setting);
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return toResponse(setting);
    }

    @Override
    public boolean webSearchUsable() {
        return getSetting().webSearchUsable();
    }

    @Override
    public boolean webFetchEnabled() {
        return getSetting().webFetchEnabled();
    }

    /** Decrypted search API key, or null when unset. */
    public String searchApiKey() {
        return cryptoService.decrypt(getSetting().getWebSearchApiKey());
    }

    private ToolSettingResponse toResponse(ToolSetting setting) {
        return new ToolSettingResponse(setting.webSearchEnabled(), setting.getWebSearchEndpoint(),
                StringUtils.hasText(setting.getWebSearchApiKey()), setting.webFetchEnabled(),
                setting.maxResultsOr(5),
                setting.getUpdateTime() == null ? null : siteSettingService.formatDateTime(setting.getUpdateTime()));
    }
}
