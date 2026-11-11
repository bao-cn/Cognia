package bao.gabriel.cognia.rag.service.impl;

import bao.gabriel.cognia.rag.service.DebugSettingService;
import bao.gabriel.cognia.system.dto.trace.DebugSettingResponse;
import bao.gabriel.cognia.system.dto.trace.UpdateDebugSettingRequest;
import bao.gabriel.cognia.system.entity.DebugSetting;
import bao.gabriel.cognia.system.entity.User;
import bao.gabriel.cognia.system.mapper.DebugSettingMapper;
import bao.gabriel.cognia.system.mapper.UserMapper;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebugSettingServiceImpl implements DebugSettingService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final DebugSettingMapper mapper;
    private final UserMapper userMapper;
    private final SiteSettingService siteSettingService;

    private volatile DebugSetting cached;
    private volatile long cachedAt;

    @Override
    public boolean enabled() {
        return setting().enabled();
    }

    @Override
    public String hardcodedUsername() {
        return setting().username();
    }

    @Override
    public boolean shouldTrace(Long userId) {
        if (userId == null) {
            return false;
        }
        DebugSetting current = setting();
        if (!current.enabled()) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && current.username().equalsIgnoreCase(user.getUsername());
    }

    @Override
    public DebugSettingResponse getSettings() {
        DebugSetting setting = setting();
        return toResponse(setting);
    }

    @Override
    @Transactional
    public DebugSettingResponse updateSettings(UpdateDebugSettingRequest request, Long operatorId) {
        DebugSetting setting = load();
        if (request != null && request.enabled() != null) {
            setting.setEnabled(request.enabled());
        }
        setting.setUpdatedBy(operatorId);
        setting.setUpdatedAt(LocalDateTime.now());
        if (setting.getId() == null) {
            setting.setId(DebugSetting.SINGLETON_ID);
            setting.setCreateTime(LocalDateTime.now());
            mapper.insert(setting);
        } else {
            mapper.updateById(setting);
        }
        cached = setting;
        cachedAt = System.currentTimeMillis();
        log.info("Dev mode {} by user {}", setting.enabled() ? "enabled" : "disabled", operatorId);
        return toResponse(setting);
    }

    private DebugSetting setting() {
        long now = System.currentTimeMillis();
        DebugSetting current = cached;
        if (current != null && now - cachedAt < CACHE_TTL.toMillis()) {
            return current;
        }
        synchronized (this) {
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL.toMillis()) {
                return cached;
            }
            cached = load();
            cachedAt = System.currentTimeMillis();
            return cached;
        }
    }

    private DebugSetting load() {
        DebugSetting setting = mapper.selectById(DebugSetting.SINGLETON_ID);
        if (setting == null) {
            setting = new DebugSetting();
            setting.setId(DebugSetting.SINGLETON_ID);
            setting.setEnabled(false);
            setting.setHardcodedUsername("admin");
            setting.setCreateTime(LocalDateTime.now());
            mapper.insert(setting);
        }
        return setting;
    }

    private DebugSettingResponse toResponse(DebugSetting setting) {
        String updatedAt = setting.getUpdatedAt() == null
                ? null
                : siteSettingService.formatDateTime(setting.getUpdatedAt());
        return new DebugSettingResponse(setting.enabled(), setting.username(), updatedAt);
    }
}
