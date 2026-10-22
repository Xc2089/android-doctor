package com.androiddoctor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String allowedOrigins,
        int maxLogChars,
        int historyLimit,
        int rateLimitPerMinute,
        Ai ai,
        Admin admin
) {
    public record Ai(
            boolean enabled,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds,
            int maxInputChars,
            String masterKeyPath
    ) {}

    public record Admin(
            String password,
            String passwordFile,
            int sessionHours
    ) {}
}

