package com.androiddoctor.service;

import com.androiddoctor.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

@Service
public class LocalMasterKeyService {
    private final Path keyPath;
    private volatile SecretKey cached;

    public LocalMasterKeyService(AppProperties properties) {
        this.keyPath = resolvePath(properties.ai().masterKeyPath(), ".key");
    }

    public Path keyPath() {
        return keyPath;
    }

    public SecretKey key() {
        SecretKey value = cached;
        if (value != null) return value;
        synchronized (this) {
            if (cached == null) cached = loadOrCreate();
            return cached;
        }
    }

    private SecretKey loadOrCreate() {
        try {
            if (Files.exists(keyPath)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(keyPath, StandardCharsets.US_ASCII).strip());
                if (decoded.length != 32) throw new IllegalStateException("本地主密钥长度无效");
                return new SecretKeySpec(decoded, "AES");
            }
            Path parent = keyPath.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            Files.writeString(keyPath, Base64.getEncoder().encodeToString(raw), StandardCharsets.US_ASCII, CREATE_NEW, WRITE);
            restrictPermissions(keyPath);
            return new SecretKeySpec(raw, "AES");
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法读取或创建本地加密主密钥", exception);
        }
    }

    static Path resolvePath(String configured, String suffix) {
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        String dbPath = System.getenv().getOrDefault("DB_PATH", "./data/android-doctor");
        return Path.of(dbPath + suffix).toAbsolutePath().normalize();
    }

    static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows ACLs are inherited from the private application data directory.
        }
    }
}

