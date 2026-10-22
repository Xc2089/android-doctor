package com.androiddoctor.service;

import com.androiddoctor.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthServiceTest {
    @TempDir Path tempDir;

    @Test
    void acceptsCorrectPasswordAndRejectsIncorrectPassword() {
        var service = new AdminAuthService(properties("strong-test-password"));
        assertThat(service.login("incorrect", "127.0.0.1").success()).isFalse();
        var result = service.login("strong-test-password", "127.0.0.1");
        assertThat(result.success()).isTrue();
        assertThat(result.token()).hasSizeGreaterThan(30);
    }

    @Test
    void rateLimitsRepeatedFailures() {
        var service = new AdminAuthService(properties("strong-test-password"));
        for (int i = 0; i < 5; i++) service.login("incorrect", "10.0.0.2");
        assertThat(service.login("incorrect", "10.0.0.2").rateLimited()).isTrue();
    }

    private AppProperties properties(String password) {
        return new AppProperties(
                "http://localhost:9090", 1_000_000, 100, 30,
                new AppProperties.Ai(false, "http://localhost:11434/v1", "", "local", 45,
                        24_000, tempDir.resolve("master.key").toString()),
                new AppProperties.Admin(password, tempDir.resolve("admin-password").toString(), 8));
    }
}

