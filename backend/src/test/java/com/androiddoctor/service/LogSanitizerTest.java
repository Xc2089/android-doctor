package com.androiddoctor.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {
    private final LogSanitizer sanitizer = new LogSanitizer();

    @Test
    void redactsCommonSecretsAndPersonalData() {
        String input = "Authorization: Bearer top-secret\nemail=dev@example.com\n" +
                "android_id=abcdef1234567890\npath=/home/alice/project\nMAC=AA:BB:CC:DD:EE:FF\nserver=192.168.1.42";
        var result = sanitizer.sanitize(input);
        assertThat(result.value()).doesNotContain("top-secret", "dev@example.com", "abcdef1234567890", "/home/alice", "AA:BB:CC:DD:EE:FF", "192.168.1.42");
        assertThat(result.redactionCount()).isEqualTo(6);
    }
}

