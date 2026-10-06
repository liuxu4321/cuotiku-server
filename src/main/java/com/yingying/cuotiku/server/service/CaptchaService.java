package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CaptchaService.class);

    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int WIDTH = 130;
    private static final int HEIGHT = 44;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;

    public CaptchaService(AppProperties properties) {
        this.properties = properties;
    }

    private record Entry(String code, long expiresAtMillis) {}

    public record Captcha(String captchaId, String imageBase64, long expiresInSeconds) {}

    public Captcha generate() {
        int length = properties.captcha().length();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i += 1) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        long ttlSeconds = properties.captcha().ttlSeconds();
        store.put(captchaId, new Entry(code.toString(), System.currentTimeMillis() + ttlSeconds * 1000));
        if (log.isDebugEnabled()) {
            log.debug("[验证码] 生成 captchaId={} 位数={} 有效期={}s 当前缓存数={}",
                    captchaId, length, ttlSeconds, store.size());
        }
        return new Captcha(captchaId, render(code.toString()), ttlSeconds);
    }

    public boolean verifyAndConsume(String captchaId, String input) {
        if (captchaId == null || input == null) return false;
        Entry entry = store.remove(captchaId);
        boolean ok = entry != null
                && entry.expiresAtMillis() >= System.currentTimeMillis()
                && entry.code().equalsIgnoreCase(input.trim());
        if (log.isDebugEnabled()) {
            log.debug("[验证码] 校验并作废 captchaId={} 存在={} 结果={}", captchaId, entry != null, ok);
        }
        return ok;
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        long now = System.currentTimeMillis();
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().expiresAtMillis() < now);
        if (log.isDebugEnabled() && store.size() < before) {
            log.debug("[验证码] 定时清理过期 {} 条，剩余 {} 条", before - store.size(), store.size());
        }
    }

    private String render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i += 1) {
            g.setColor(randomLightColor());
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        for (int i = 0; i < 60; i += 1) {
            g.setColor(randomLightColor());
            g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }
        int charWidth = WIDTH / (code.length() + 1);
        for (int i = 0; i < code.length(); i += 1) {
            g.setColor(new Color(20 + random.nextInt(110), 20 + random.nextInt(110), 20 + random.nextInt(110)));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26 + random.nextInt(6)));
            double angle = (random.nextDouble() - 0.5) * 0.5;
            int x = charWidth / 2 + i * charWidth + random.nextInt(6);
            int y = HEIGHT / 2 + 10 + random.nextInt(5) - 2;
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Color randomLightColor() {
        return new Color(160 + random.nextInt(80), 160 + random.nextInt(80), 160 + random.nextInt(80));
    }
}
