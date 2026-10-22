package com.androiddoctor.service;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineServiceTest {
    private final RuleEngineService engine = new RuleEngineService();

    @Test
    void detectsNativeLibraryAndAbiMismatch() {
        var results = engine.analyze("java.lang.UnsatisfiedLinkError: dlopen failed: libfoo.so is 32-bit instead of 64-bit");
        Set<String> codes = results.stream().map(item -> item.code()).collect(Collectors.toSet());
        assertThat(codes).contains("NATIVE_LIB_NOT_FOUND", "ABI_MISMATCH");
    }

    @Test
    void detectsJniAndSixteenKilobytePageIssue() {
        var results = engine.analyze("JNI DETECTED ERROR: no implementation found for foo; LOAD segment alignment is 4 KB, expected 16KB page size");
        Set<String> codes = results.stream().map(item -> item.code()).collect(Collectors.toSet());
        assertThat(codes).contains("JNI_SIGNATURE", "PAGE_SIZE_16K");
    }

    @Test
    void detectsAnrOomSelinuxAndSerial() {
        String log = "ANR in com.demo: Input dispatching timed out\n" +
                "java.lang.OutOfMemoryError: Failed to allocate\n" +
                "avc: denied { read } for /dev/ttyS4 scontext=u:r:untrusted_app:s0 tcontext=u:object_r:serial_device:s0\n" +
                "SerialPort failed to open /dev/ttyS4: permission denied";
        Set<String> codes = engine.analyze(log).stream().map(item -> item.code()).collect(Collectors.toSet());
        assertThat(codes).contains("ANR", "OUT_OF_MEMORY", "SELINUX_DENIAL", "SERIAL_PORT");
    }

    @Test
    void detectsHarmonySigabrt() {
        var results = engine.analyze("OHOS FaultLogger: Fatal signal 6 (SIGABRT), code -1 in tid 123 (ArkUIThread)");
        assertThat(results).extracting("code").contains("HARMONY_SIGABRT");
    }

    @Test
    void returnsUnknownFallback() {
        var results = engine.analyze("A harmless informational line");
        assertThat(results).singleElement().extracting("code").isEqualTo("UNKNOWN");
    }
}

