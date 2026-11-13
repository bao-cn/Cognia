package bao.gabriel.cognia.config;

import bao.gabriel.cognia.system.entity.LegalDocument;
import bao.gabriel.cognia.system.mapper.LegalDocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Ships the open-source license with the application. The document is read
 * from a classpath resource and refreshed on startup so it can never drift
 * from the binary that is actually distributed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class OpenSourceLicenseInitializer implements ApplicationRunner {

    private static final String RESOURCE = "legal/open-source-license.md";
    private static final int VERSION = 2;

    private final LegalDocumentMapper legalDocumentMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ClassPathResource resource = new ClassPathResource(RESOURCE);
            if (!resource.exists()) {
                log.warn("Open-source license resource {} is missing", RESOURCE);
                return;
            }
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
            LegalDocument document = legalDocumentMapper.selectOne(new LambdaQueryWrapper<LegalDocument>()
                    .eq(LegalDocument::getDocType, LegalDocument.TYPE_OPEN_SOURCE)
                    .last("LIMIT 1"));
            if (document == null) {
                document = new LegalDocument();
                document.setDocType(LegalDocument.TYPE_OPEN_SOURCE);
                document.setTitle("开放源代码许可");
                document.setVersion(VERSION);
                document.setContent(content);
                document.setPublishedAt(LocalDateTime.now());
                document.setCreateTime(LocalDateTime.now());
                document.setUpdateTime(LocalDateTime.now());
                legalDocumentMapper.insert(document);
                log.info("Seeded open-source license from {}", RESOURCE);
                return;
            }
            if (content.equals(document.getContent()) && Integer.valueOf(VERSION).equals(document.getVersion())) {
                return;
            }
            document.setTitle("开放源代码许可");
            document.setContent(content);
            document.setVersion(VERSION);
            document.setPublishedAt(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            legalDocumentMapper.updateById(document);
            log.info("Refreshed open-source license from {}", RESOURCE);
        } catch (Exception e) {
            log.error("Failed to load open-source license", e);
        }
    }
}
