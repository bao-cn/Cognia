package bao.gabriel.cognia.config;

import bao.gabriel.cognia.system.entity.ModelProvider;
import bao.gabriel.cognia.system.mapper.ModelProviderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class OllamaAutoConfig implements ApplicationRunner {

    private final ModelProviderMapper providerMapper;

    @Value("${OLLAMA_BASE_URL:${app.ollama.base-url:}}")
    private String baseUrl;

    @Override
    public void run(ApplicationArguments args) {
        if (baseUrl == null || baseUrl.isBlank()) return;
        long count = providerMapper.selectCount(new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getVendor, "OLLAMA"));
        if (count > 0) return;
        String base = baseUrl.trim();
        if (!base.endsWith("/v1")) base = base.replaceAll("/+$", "") + "/v1";
        ModelProvider provider = new ModelProvider();
        provider.setName("Ollama（本地）");
        provider.setVendor("OLLAMA");
        provider.setBaseUrl(base);
        provider.setProtocol(ModelProvider.PROTOCOL_OPENAI_CHAT);
        provider.setEnabled(true);
        provider.setCreateTime(LocalDateTime.now());
        provider.setUpdateTime(LocalDateTime.now());
        providerMapper.insert(provider);
        log.info("Ollama provider configured: {}", base);
    }
}
