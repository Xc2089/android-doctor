package com.androiddoctor.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretCryptoService {
    private static final byte[] AAD = "android-doctor-ai-config-v1".getBytes(StandardCharsets.UTF_8);
    private final LocalMasterKeyService masterKeyService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCryptoService(LocalMasterKeyService masterKeyService) {
        this.masterKeyService = masterKeyService;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKeyService.key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return "v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法加密 API Key", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        if (!value.startsWith("v1:")) throw new IllegalStateException("不支持的密钥加密格式");
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(3));
            if (payload.length < 29) throw new IllegalStateException("加密数据无效");
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKeyService.key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("无法解密 API Key；请确认主密钥文件未被替换", exception);
        }
    }
}
