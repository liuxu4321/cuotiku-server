package com.yingying.cuotiku.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingying.cuotiku.server.ai.TencentOcrClient;
import com.yingying.cuotiku.server.config.AppProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentEraseLiveSmokeTest {

    @Test
    void eraseSmallImage() throws Exception {
        String secretId = System.getenv("TENCENT_SECRET_ID");
        String secretKey = System.getenv("TENCENT_SECRET_KEY");
        Assumptions.assumeTrue(secretId != null && !secretId.isBlank(), "未提供腾讯云密钥，跳过真实擦除冒烟");

        AppProperties properties = new AppProperties(
                null, null, null,
                new AppProperties.Ai(new AppProperties.Ai.Tencent(
                        null, null, secretId, secretKey, 60)),
                null,
                null);
        TencentOcrClient client = new TencentOcrClient(properties, new ObjectMapper());

        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 120);
        g.setColor(Color.BLACK);
        g.drawString("1+1=2", 40, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        TencentOcrClient.EraseResult result =
                client.erase(Base64.getEncoder().encodeToString(out.toByteArray()));
        assertNotNull(result.imageBase64());
        assertFalse(result.imageBase64().isBlank());
        System.out.println("擦除成功 RequestId=" + result.requestId()
                + " 返回base64长度=" + result.imageBase64().length());
    }

    @Test
    void eraseLargeImageAfterNormalize() throws Exception {
        String secretId = System.getenv("TENCENT_SECRET_ID");
        String secretKey = System.getenv("TENCENT_SECRET_KEY");
        Assumptions.assumeTrue(secretId != null && !secretId.isBlank(), "未提供腾讯云密钥，跳过大图擦除冒烟");

        AppProperties properties = new AppProperties(
                null, null, null,
                new AppProperties.Ai(new AppProperties.Ai.Tencent(
                        null, null, secretId, secretKey, 90)),
                null,
                null);
        TencentOcrClient client = new TencentOcrClient(properties, new ObjectMapper());

        // 5000x3500 超阈值图：触发服务端归一化（缩到 4096 边内）后调用擦除
        BufferedImage image = new BufferedImage(5000, 3500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 5000, 3500);
        g.setColor(Color.BLACK);
        for (int i = 0; i < 40; i += 1) {
            g.drawString("Question " + i + ": 3x + 5 = 20, x = ?", 200, 150 + i * 80);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);

        TencentOcrClient.EraseResult result =
                client.erase(Base64.getEncoder().encodeToString(out.toByteArray()));
        assertNotNull(result.imageBase64());
        assertFalse(result.imageBase64().isBlank());
        System.out.println("大图擦除成功 RequestId=" + result.requestId()
                + " 返回base64长度=" + result.imageBase64().length());
    }

    @Test
    void cropEnhanceSmallImage() throws Exception {
        String secretId = System.getenv("TENCENT_SECRET_ID");
        String secretKey = System.getenv("TENCENT_SECRET_KEY");
        Assumptions.assumeTrue(secretId != null && !secretId.isBlank(), "未提供腾讯云密钥，跳过真实切边增强冒烟");

        AppProperties properties = new AppProperties(
                null, null, null,
                new AppProperties.Ai(new AppProperties.Ai.Tencent(
                        null, null, secretId, secretKey, 60)),
                null,
                null);
        TencentOcrClient client = new TencentOcrClient(properties, new ObjectMapper());

        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 600);
        g.setColor(Color.BLACK);
        g.drawRect(60, 40, 680, 520);
        g.drawString("Math Homework", 100, 120);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);

        TencentOcrClient.CropEnhanceResult result = client.cropEnhance(
                Base64.getEncoder().encodeToString(out.toByteArray()), true, true, false, false, -1);
        assertNotNull(result.imageBytes());
        assertTrue(result.imageBytes().length > 0);
        TencentOcrClient.SplitResult split = client.splitQuestions(
                Base64.getEncoder().encodeToString(out.toByteArray()), true);
        assertNotNull(split.requestId());
        System.out.println("切题检测成功 RequestId=" + split.requestId()
                + " " + split.width() + "x" + split.height() + " 题框=" + split.boxes().size());

        System.out.println("切边增强成功 RequestId=" + result.requestId()
                + " " + result.width() + "x" + result.height()
                + " 图片bytes=" + result.imageBytes().length
                + " 角点=" + result.position() + " angle=" + result.angle());
    }
}
