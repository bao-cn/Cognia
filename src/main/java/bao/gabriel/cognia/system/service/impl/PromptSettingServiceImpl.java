package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.prompt.PromptSettingResponse;
import bao.gabriel.cognia.system.dto.prompt.UpdatePromptSettingRequest;
import bao.gabriel.cognia.system.entity.PromptSetting;
import bao.gabriel.cognia.system.mapper.PromptSettingMapper;
import bao.gabriel.cognia.system.service.PromptSettingService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PromptSettingServiceImpl implements PromptSettingService {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are Cognia, a precise knowledge-base assistant. Answer in the user's language.";
    public static final String DEFAULT_RAG_PROMPT =
            "When retrieved knowledge is provided, cite the source file name and channel, "
                    + "and base the answer on that knowledge when it is relevant.";
    public static final String DEFAULT_EXPIRED_PROMPT =
            "If a retrieved chunk is marked 状态：已过期, you must explicitly tell the user the information is expired, "
                    + "state its expiration time, and must not present it as a current fact.";
    public static final String DEFAULT_TOOL_PROMPT =
            "Use web_search and web_fetch when current external information is needed, and cite the sources you used.";
    public static final String DEFAULT_COMPRESSION_PROMPT =
            "请把下面的多轮对话历史压缩成一段简洁的中文摘要，要求：\n"
                    + "1. 保留用户的目标、已确认的事实、关键结论与未完成的事项；\n"
                    + "2. 保留对话中出现的人名、专有名词、数字、时间等关键信息；\n"
                    + "3. 删除寒暄、重复内容和无关细节；\n"
                    + "4. 只输出摘要正文，不要加标题或解释。";

    private static final long CACHE_TTL_MS = 10_000;

    private final PromptSettingMapper mapper;
    private final SiteSettingService siteSettingService;

    private volatile PromptSetting cached;
    private volatile long cachedAt;

    @Override
    public PromptSetting getSetting() {
        PromptSetting local = cached;
        if (local != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return local;
        }
        PromptSetting setting = mapper.selectById(PromptSetting.SINGLETON_ID);
        if (setting == null) {
            setting = new PromptSetting();
            setting.setId(PromptSetting.SINGLETON_ID);
            applyDefaults(setting);
            mapper.insert(setting);
        }
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return setting;
    }

    @Override
    public PromptSettingResponse getAdminSettings() {
        return toResponse(getSetting());
    }

    @Override
    public PromptSettingResponse getDefaults() {
        PromptSetting setting = new PromptSetting();
        applyDefaults(setting);
        return toResponse(setting);
    }

    @Override
    @Transactional
    public PromptSettingResponse updateSettings(UpdatePromptSettingRequest request, Long operatorId) {
        PromptSetting setting = getSetting();
        if (request.systemPrompt() != null) {
            String value = request.systemPrompt().trim();
            if (!StringUtils.hasText(value)) {
                throw BusinessException.badRequest("系统提示词不能为空");
            }
            setting.setSystemPrompt(value);
        }
        if (request.ragPrompt() != null) {
            setting.setRagPrompt(trimToNull(request.ragPrompt()));
        }
        if (request.expiredPrompt() != null) {
            setting.setExpiredPrompt(trimToNull(request.expiredPrompt()));
        }
        if (request.toolPrompt() != null) {
            setting.setToolPrompt(trimToNull(request.toolPrompt()));
        }
        if (request.compressionPrompt() != null) {
            String value = request.compressionPrompt().trim();
            if (!StringUtils.hasText(value)) {
                throw BusinessException.badRequest("上下文压缩提示词不能为空");
            }
            setting.setCompressionPrompt(value);
        }
        setting.setUpdatedBy(operatorId);
        setting.setUpdateTime(LocalDateTime.now());
        mapper.updateById(setting);
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return toResponse(setting);
    }

    @Override
    @Transactional
    public PromptSettingResponse reset(Long operatorId) {
        PromptSetting setting = getSetting();
        applyDefaults(setting);
        setting.setUpdatedBy(operatorId);
        setting.setUpdateTime(LocalDateTime.now());
        mapper.updateById(setting);
        cached = setting;
        cachedAt = System.currentTimeMillis();
        return toResponse(setting);
    }

    private void applyDefaults(PromptSetting setting) {
        setting.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        setting.setRagPrompt(DEFAULT_RAG_PROMPT);
        setting.setExpiredPrompt(DEFAULT_EXPIRED_PROMPT);
        setting.setToolPrompt(DEFAULT_TOOL_PROMPT);
        setting.setCompressionPrompt(DEFAULT_COMPRESSION_PROMPT);
        setting.setUpdateTime(LocalDateTime.now());
    }

    private PromptSettingResponse toResponse(PromptSetting setting) {
        return new PromptSettingResponse(
                setting.getSystemPrompt(),
                setting.getRagPrompt(),
                setting.getExpiredPrompt(),
                setting.getToolPrompt(),
                setting.getCompressionPrompt(),
                setting.getUpdateTime() == null ? null : siteSettingService.formatDateTime(setting.getUpdateTime()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
