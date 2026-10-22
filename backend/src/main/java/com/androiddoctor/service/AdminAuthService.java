package com.androiddoctor.service;

import com.androiddoctor.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

@Service
public class AdminAuthService {
    public static final String COOKIE_NAME = "AD_ADMIN";
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private static final int PBKDF2_ITERATIONS = 120_000;
    private final byte[] passwordSalt = new byte[16];
    private final byte[] passwordHash;
    private final Duration sessionDuration;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final String passwordSource;
    private final String passwordHint;

    public AdminAuthService(AppProperties properties) {
        random.nextBytes(passwordSalt);
        this.sessionDuration = Duration.ofHours(Math.max(1, Math.min(properties.admin().sessionHours(), 72)));
        String configuredPassword = properties.admin().password();
        if (configuredPassword != null && !configuredPassword.isBlank()) {
            passwordSource = "environment";
            passwordHint = "APP_ADMIN_PASSWORD";
            passwordHash = derive(configuredPassword);
        } else {
            Path path = LocalMasterKeyService.resolvePath(properties.admin().passwordFile(), ".admin-password");
            passwordSource = "generated-file";
            passwordHint = path.toString();
            passwordHash = derive(loadOrCreatePassword(path));
            log.info("Admin password is stored in the local file: {}", path);
        }
    }

    public LoginResult login(String password, String remoteAddress) {
        long minute = Instant.now().getEpochSecond() / 60;
        AttemptWindow window = attempts.compute(remoteAddress, (ignored, old) ->
                old == null || old.minute != minute ? new AttemptWindow(minute) : old);
        if (window.count.incrementAndGet() > 5) return new LoginResult(null, true);
        if (!MessageDigest.isEqual(passwordHash, derive(password))) return new LoginResult(null, false);
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, Instant.now().plus(sessionDuration));
        prune();
        return new LoginResult(token, false);
    }

    public boolean isAuthorized(HttpServletRequest request) {
        String token = token(request);
        if (token == null) return false;
        Instant expiry = sessions.get(token);
        if (expiry == null || expiry.isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void logout(HttpServletRequest request) {
        String token = token(request);
        if (token != null) sessions.remove(token);
    }

    public long sessionSeconds() { return sessionDuration.toSeconds(); }
    public String passwordSource() { return passwordSource; }
    public String passwordHint() { return passwordHint; }

    private String loadOrCreatePassword(Path path) {
        try {
            if (Files.exists(path)) {
                String value = Files.readString(path, StandardCharsets.UTF_8).strip();
                if (value.isBlank()) throw new IllegalStateException("管理员密码文件为空");
                return value;
            }
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            byte[] bytes = new byte[18];
            random.nextBytes(bytes);
            String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Files.writeString(path, value, StandardCharsets.UTF_8, CREATE_NEW, WRITE);
            LocalMasterKeyService.restrictPermissions(path);
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取或创建管理员密码文件", exception);
        }
    }

    private byte[] derive(String password) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), passwordSalt, PBKDF2_ITERATIONS, 256);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化管理员认证", exception);
        }
    }

    private String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void prune() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        long minute = now.getEpochSecond() / 60;
        attempts.entrySet().removeIf(entry -> entry.getValue().minute < minute - 2);
    }

    public record LoginResult(String token, boolean rateLimited) {
        public boolean success() { return token != null; }
    }

    private static final class AttemptWindow {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private AttemptWindow(long minute) { this.minute = minute; }
    }
}

