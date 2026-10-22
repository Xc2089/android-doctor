package com.androiddoctor.dto;

import java.util.List;

public record FindingDto(
        String code,
        String title,
        String category,
        String severity,
        int confidence,
        List<String> evidence,
        List<String> possibleCauses,
        List<String> suggestions
) {}

