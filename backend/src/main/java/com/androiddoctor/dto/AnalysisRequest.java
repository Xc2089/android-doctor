package com.androiddoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalysisRequest(
        @NotBlank(message = "日志内容不能为空")
        @Size(max = 1_000_000, message = "日志不能超过 1,000,000 个字符")
        String log,
        @Size(max = 255, message = "来源名称过长")
        String sourceName,
        Boolean useAi
) {}

