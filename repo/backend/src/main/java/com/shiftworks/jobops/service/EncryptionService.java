package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec keySpec;

    @PostConstruct
    void init() {
        String raw = appProperties.getSecurity().getAesSecretKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AES_SECRET_KEY must be configured");
        }

        byte[] keyBytes;
        try {
            if (raw.length() == 32) {
                keyBytes = raw.getBytes(StandardCharsets.UTF_8);
            } else {
                keyBytes = Base64.getDecoder().decode(raw);
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AES_SECRET_KEY must be a 32-character string or a valid Base64-encoded AES key", ex);
        }

        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("AES_SECRET_KEY must decode to a 16, 24, or 32 byte AES key");
        }

        keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Encryption failed", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Decryption failed", ex);
        }
    }
}
