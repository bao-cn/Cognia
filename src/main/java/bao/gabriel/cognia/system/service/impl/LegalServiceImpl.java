package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.legal.ConsentStatusResponse;
import bao.gabriel.cognia.system.dto.legal.LegalDocumentResponse;
import bao.gabriel.cognia.system.dto.legal.SubmitConsentRequest;
import bao.gabriel.cognia.system.dto.legal.UpdateLegalDocumentRequest;
import bao.gabriel.cognia.system.entity.LegalDocument;
import bao.gabriel.cognia.system.entity.UserConsent;
import bao.gabriel.cognia.system.mapper.LegalDocumentMapper;
import bao.gabriel.cognia.system.mapper.UserConsentMapper;
import bao.gabriel.cognia.system.service.LegalService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Privacy policy / terms versioning and per-user consent tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalServiceImpl implements LegalService {

    private static final String PLACEHOLDER_CONTENT = "该文档尚未配置，请联系平台管理员。";

    private final LegalDocumentMapper documentMapper;
    private final UserConsentMapper consentMapper;
    private final SiteSettingService settingService;

    @Override
    public List<LegalDocumentResponse> listDocuments() {
        return ensureDocuments().stream().map(this::toResponse).toList();
    }

    @Override
    public ConsentStatusResponse getConsentStatus(Long userId) {
        List<LegalDocument> documents = ensureDocuments();
        List<LegalDocument> requiredDocuments = consentDocuments(documents);
        Map<String, Integer> consents = consentMapper.selectList(
                        new LambdaQueryWrapper<UserConsent>().eq(UserConsent::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserConsent::getDocType, UserConsent::getVersion,
                        (left, right) -> right, LinkedHashMap::new));

        boolean requiresConsent = requiredDocuments.stream()
                .anyMatch(document -> {
                    Integer accepted = consents.get(document.getDocType());
                    return accepted == null || accepted < document.getVersion();
                });

        return new ConsentStatusResponse(
                requiredDocuments.stream().map(this::toResponse).toList(),
                consents,
                requiresConsent
        );
    }

    @Override
    @Transactional
    public ConsentStatusResponse submitConsent(Long userId, SubmitConsentRequest request) {
        List<LegalDocument> documents = ensureDocuments();
        Map<String, LegalDocument> byType = documents.stream()
                .collect(Collectors.toMap(LegalDocument::getDocType, Function.identity()));

        LegalDocument privacy = byType.get(LegalDocument.TYPE_PRIVACY);
        LegalDocument terms = byType.get(LegalDocument.TYPE_TERMS);
        if (privacy == null || terms == null) {
            throw BusinessException.notFound("法律文档尚未初始化");
        }
        if (!Objects.equals(request.privacyVersion(), privacy.getVersion())
                || !Objects.equals(request.termsVersion(), terms.getVersion())) {
            throw BusinessException.conflict("协议内容已更新，请刷新页面后重新阅读");
        }

        LocalDateTime now = LocalDateTime.now();
        upsert(userId, privacy, now);
        upsert(userId, terms, now);
        log.info("User {} accepted legal documents privacy v{} / terms v{}",
                userId, privacy.getVersion(), terms.getVersion());
        return getConsentStatus(userId);
    }

    @Override
    @Transactional
    public LegalDocumentResponse publish(String type, UpdateLegalDocumentRequest request, Long operatorId) {
        String normalizedType = normalizeType(type);
        if (LegalDocument.TYPE_OPEN_SOURCE.equals(normalizedType)) {
            throw BusinessException.forbidden("开放源代码许可随程序分发，不支持在线编辑或修订");
        }
        LegalDocument document = ensureDocument(normalizedType);
        boolean contentChanged = !Objects.equals(document.getContent(), request.content());

        document.setTitle(request.title().trim());
        document.setContent(request.content());
        document.setUpdatedBy(operatorId);
        document.setUpdateTime(LocalDateTime.now());
        if (contentChanged) {
            document.setVersion(document.getVersion() == null ? 2 : document.getVersion() + 1);
            document.setPublishedAt(LocalDateTime.now());
            log.info("Published {} v{}", normalizedType, document.getVersion());
        }
        documentMapper.updateById(document);
        return toResponse(document);
    }

    @Override
    @Transactional
    public void recordConsentForNewUser(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (LegalDocument document : consentDocuments(ensureDocuments())) {
            upsert(userId, document, now);
        }
    }

    private void upsert(Long userId, LegalDocument document, LocalDateTime consentedAt) {
        UserConsent existing = consentMapper.selectOne(new LambdaQueryWrapper<UserConsent>()
                .eq(UserConsent::getUserId, userId)
                .eq(UserConsent::getDocType, document.getDocType())
                .last("LIMIT 1"));
        if (existing == null) {
            consentMapper.insert(new UserConsent(userId, document.getDocType(),
                    document.getVersion(), consentedAt));
        } else {
            existing.setVersion(document.getVersion());
            existing.setConsentedAt(consentedAt);
            consentMapper.updateById(existing);
        }
    }

    private List<LegalDocument> ensureDocuments() {
        return List.of(ensureDocument(LegalDocument.TYPE_PRIVACY),
                ensureDocument(LegalDocument.TYPE_TERMS),
                ensureDocument(LegalDocument.TYPE_OPEN_SOURCE));
    }

    /**
     * Open-source licenses are informative only. They are published and
     * versioned with the legal documents but never participate in consent.
     */
    private List<LegalDocument> consentDocuments(List<LegalDocument> documents) {
        return documents.stream()
                .filter(document -> LegalDocument.TYPE_PRIVACY.equals(document.getDocType())
                        || LegalDocument.TYPE_TERMS.equals(document.getDocType()))
                .toList();
    }

    private LegalDocument ensureDocument(String type) {
        LegalDocument document = documentMapper.selectOne(new LambdaQueryWrapper<LegalDocument>()
                .eq(LegalDocument::getDocType, type)
                .last("LIMIT 1"));
        if (document != null) {
            return document;
        }
        document = new LegalDocument();
        document.setDocType(type);
        document.setTitle(switch (type) {
            case LegalDocument.TYPE_PRIVACY -> "隐私政策";
            case LegalDocument.TYPE_TERMS -> "用户协议";
            default -> "开放源代码许可";
        });
        document.setContent(PLACEHOLDER_CONTENT);
        document.setVersion(1);
        document.setPublishedAt(LocalDateTime.now());
        document.setCreateTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        documentMapper.insert(document);
        return document;
    }

    private String normalizeType(String type) {
        if (type == null) {
            throw BusinessException.badRequest("文档类型不能为空");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!LegalDocument.TYPE_PRIVACY.equals(normalized)
                && !LegalDocument.TYPE_TERMS.equals(normalized)
                && !LegalDocument.TYPE_OPEN_SOURCE.equals(normalized)) {
            throw BusinessException.badRequest("文档类型只能为 PRIVACY、TERMS 或 OPEN_SOURCE");
        }
        return normalized;
    }

    private LegalDocumentResponse toResponse(LegalDocument document) {
        return new LegalDocumentResponse(
                String.valueOf(document.getId()),
                document.getDocType(),
                document.getTitle(),
                document.getContent(),
                document.getVersion() == null ? 1 : document.getVersion(),
                settingService.formatDateTime(document.getPublishedAt()),
                settingService.formatDateTime(document.getUpdateTime())
        );
    }
}
