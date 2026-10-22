package com.androiddoctor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiConfigUpdateRequest(
        boolean enabled,
        @NotBlank(message = "Base URL 不能为空")
        @Size(max = 500, message = "Base URL 过长")
        String baseUrl,
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 200, message = "模型名称过长")
        String model,
        @Min(value = 5, message = "超时不能少于 5 秒")
        @Max(value = 120, message = "超时不能超过 120 秒")
        int timeoutSeconds,
        @Size(max = 4096, message = "API Key 过长")
        String apiKey,
        boolean clearApiKey
) {}

