package com.androiddoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiagnosisResponse(
        UUID id,
        Instant createdAt,
        String sourceName,
        String category,
        String severity,
        String summary,
        String sanitizedLog,
        int redactionCount,
        List<FindingDto> findings,
        boolean aiUsed,
        String aiAnalysis,
        String aiWarning
) {}

