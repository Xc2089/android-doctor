package com.androiddoctor.dto;

public record AiConfigView(
        boolean enabled,
        String model,
        String baseUrl,
        int timeoutSeconds,
        boolean apiKeyConfigured,
        String source
) {}

