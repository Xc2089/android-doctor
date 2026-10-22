package com.androiddoctor.dto;

import java.time.Instant;
import java.util.UUID;

public record HistoryItem(
        UUID id,
        Instant createdAt,
        String sourceName,
        String category,
        String severity,
        String summary,
        boolean aiUsed,
        int redactionCount
) {}

