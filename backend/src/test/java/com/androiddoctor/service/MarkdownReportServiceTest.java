package com.androiddoctor.service;

import com.androiddoctor.dto.DiagnosisResponse;
import com.androiddoctor.dto.FindingDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownReportServiceTest {
    @Test
    void rendersSanitizedEvidenceAndSuggestions() {
        FindingDto finding = new FindingDto("ABI_MISMATCH", "ABI 不匹配", "ABI", "critical", 97,
                List.of("wrong ELF class"), List.of("架构不一致"), List.of("重新编译 native 库"));
        DiagnosisResponse diagnosis = new DiagnosisResponse(UUID.randomUUID(), Instant.parse("2026-10-22T02:00:00Z"),
                "sample.log", "ABI", "critical", "ABI 不匹配", "email=[REDACTED_EMAIL]", 1,
                List.of(finding), false, null, null);

        String report = new MarkdownReportService().render(diagnosis);
        assertThat(report).contains("# Android Doctor 诊断报告", "ABI_MISMATCH", "重新编译 native 库", "[REDACTED_EMAIL]");
    }
}
