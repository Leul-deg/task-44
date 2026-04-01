package com.shiftworks.jobops.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int WIDTH = 180;
    private static final int HEIGHT = 60;
    private static final long TTL_SECONDS = 300;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    public Captcha generateCaptcha() {
        String answer = randomText(5);
        String id = UUID.randomUUID().toString();
        captchaStore.put(id, new CaptchaEntry(answer, Instant.now().plusSeconds(TTL_SECONDS)));
        return new Captcha(id, createImage(answer));
    }

    public boolean validate(String id, String answer) {
        if (id == null || answer == null) {
            return false;
        }
        CaptchaEntry entry = captchaStore.get(id);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            captchaStore.remove(id);
            return false;
        }
        boolean matches = entry.answer().equalsIgnoreCase(answer.trim());
        if (matches) {
            captchaStore.remove(id);
        }
        return matches;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Instant now = Instant.now();
        captchaStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String randomText(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(CAPTCHA_CHARS.length());
            builder.append(CAPTCHA_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private String createImage(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(240, 243, 255));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 32));
        for (int i = 0; i < 15; i++) {
            graphics.setColor(new Color(secureRandom.nextInt(255), secureRandom.nextInt(255), secureRandom.nextInt(255)));
            int x1 = secureRandom.nextInt(WIDTH);
            int y1 = secureRandom.nextInt(HEIGHT);
            int x2 = secureRandom.nextInt(WIDTH);
            int y2 = secureRandom.nextInt(HEIGHT);
            graphics.drawLine(x1, y1, x2, y2);
        }
        graphics.setColor(new Color(55, 71, 133));
        int x = 20;
        for (char c : text.toCharArray()) {
            int y = 30 + secureRandom.nextInt(20);
            graphics.drawChars(new char[]{c}, 0, 1, x, y);
            x += 30;
        }
        graphics.dispose();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render captcha", ex);
        }
    }

    public record Captcha(String captchaId, String imageBase64) {
    }

    private record CaptchaEntry(String answer, Instant expiresAt) {
    }
}
