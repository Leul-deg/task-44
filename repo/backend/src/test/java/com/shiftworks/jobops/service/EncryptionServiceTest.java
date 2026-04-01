package com.shiftworks.jobops.service;

import com.shiftworks.jobops.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        AppProperties.Security sec = new AppProperties.Security();
        sec.setAesSecretKey("12345678901234567890123456789012");
        props.setSecurity(sec);
        encryptionService = new EncryptionService(props);
        encryptionService.init();
    }

    @Test
    void encryptDecryptRoundtrip() {
        String original = "Hello World 555-123-4567";
        String encrypted = encryptionService.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void differentCiphertextEachTime() {
        String value = "same-value";
        String first = encryptionService.encrypt(value);
        String second = encryptionService.encrypt(value);
        assertNotEquals(first, second, "Random IV should produce different ciphertext");
        assertEquals(value, encryptionService.decrypt(first));
        assertEquals(value, encryptionService.decrypt(second));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
    }
}
