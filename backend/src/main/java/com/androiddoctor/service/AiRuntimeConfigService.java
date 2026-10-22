package com.androiddoctor.service;

import com.androiddoctor.config.AppProperties;
import com.androiddoctor.dto.AiConfigUpdateRequest;
import com.androiddoctor.dto.AiConfigView;
import com.androiddoctor.model.AiConfiguration;
import com.androiddoctor.repository.AiConfigurationRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiRuntimeConfigService {
    private static final Logger log = LoggerFactory.getLogger(AiRuntimeConfigService.class);
    private static final long CONFIG_ID = 1L;
    private final AtomicReference<AiSettings> current;
    private final int maxInputChars;
    private final AiSettings environmentDefaults;
    private final AiConfigurationRepository repository;
    private final SecretCryptoService cryptoService;

    public AiRuntimeConfigService(AppProperties properties, AiConfigurationRepository repository,
                                  SecretCryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.maxInputChars = properties.ai().maxInputChars();
        this.environmentDefaults = new AiSettings(
                properties.ai().enabled(),
                normalizeBaseUrl(properties.ai().baseUrl()),
                valueOrDefault(properties.ai().model(), "qwen2.5-coder:7b"),
                properties.ai().apiKey() == null ? "" : properties.ai().apiKey(),
                clampTimeout(properties.ai().timeoutSeconds()),
                maxInputChars,
                "environment"
        );
        this.current = new AtomicReference<>(environmentDefaults);
    }

    @PostConstruct
    void loadPersistedConfiguration() {
        repository.findById(CONFIG_ID).ifPresent(entity -> {
            try {
                String apiKey = cryptoService.decrypt(entity.getEncryptedApiKey());
                current.set(new AiSettings(entity.isEnabled(), entity.getBaseUrl(), entity.getModel(), apiKey,
                        clampTimeout(entity.getTimeoutSeconds()), maxInputChars, "database"));
            } catch (RuntimeException exception) {
                log.warn("Stored AI configuration could not be decrypted; environment defaults will be used");
            }
        });
    }

    public AiSettings settings() {
        return current.get();
    }

    public AiConfigView view() {
        AiSettings settings = current.get();
        return new AiConfigView(settings.enabled(), settings.model(), settings.baseUrl(),
                settings.timeoutSeconds(), !settings.apiKey().isBlank(), settings.source());
    }

    @Transactional
    public AiConfigView update(AiConfigUpdateRequest request) {
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        validateBaseUrl(baseUrl);
        String model = request.model().strip();
        if (model.isBlank()) throw new IllegalArgumentException("模型名称不能为空");

        AiSettings previous = current.get();
        String apiKey = previous.apiKey();
        if (request.clearApiKey()) apiKey = "";
        else if (request.apiKey() != null && !request.apiKey().isBlank()) apiKey = request.apiKey().strip();
        AiSettings next = new AiSettings(request.enabled(), baseUrl, model, apiKey,
                clampTimeout(request.timeoutSeconds()), maxInputChars, "database");
        repository.save(new AiConfiguration(CONFIG_ID, next.enabled(), next.baseUrl(), next.model(),
                next.timeoutSeconds(), cryptoService.encrypt(next.apiKey()), Instant.now()));
        current.set(next);
        return view();
    }

    @Transactional
    public AiConfigView resetToEnvironment() {
        repository.deleteById(CONFIG_ID);
        current.set(environmentDefaults);
        return view();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = valueOrDefault(value, "http://localhost:11434/v1").strip();
        return normalized.replaceAll("/+$", "");
    }

    private static void validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("Base URL 必须是有效的 HTTP/HTTPS 地址");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Base URL 不能包含账号、查询参数或片段");
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Base URL")) throw exception;
            throw new IllegalArgumentException("Base URL 格式无效");
        }
    }

    private static int clampTimeout(int value) {
        return Math.max(5, Math.min(value, 120));
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record AiSettings(
            boolean enabled,
            String baseUrl,
            String model,
            String apiKey,
            int timeoutSeconds,
            int maxInputChars,
            String source
    ) {}
}
