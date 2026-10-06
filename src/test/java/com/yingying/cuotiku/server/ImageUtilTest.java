package com.yingying.cuotiku.server;

import com.yingying.cuotiku.server.ai.ImageUtil;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilTest {

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
                'I', 'I', 42, 0, 8, 0, 0, 0,
                1, 0,
                0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0,
                0, 0, 0, 0
        };
        byte[] payload = new byte[6 + tiff.length];
        System.arraycopy("Exif\0\0".getBytes(), 0, payload, 0, 6);
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        int segmentLength = payload.length + 2;
        byte[] out = new byte[jpeg.length + 2 + payload.length + 2];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;
        out[2] = (byte) 0xFF;
        out[3] = (byte) 0xE1;
        out[4] = (byte) (segmentLength >> 8);
        out[5] = (byte) (segmentLength & 0xFF);
        System.arraycopy(payload, 0, out, 6, payload.length);
        System.arraycopy(jpeg, 2, out, 6 + payload.length, jpeg.length - 2);
        return out;
    }

    @Test
    void readsExifOrientation() {
        assertEquals(6, ImageUtil.readExifOrientation(withExifOrientationSafe()));
    }

    private byte[] withExifOrientationSafe() {
        try {
            return withExifOrientation(jpeg(200, 100), 6);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void autoOrientRotates90Cw() throws Exception {
        byte[] oriented = ImageUtil.autoOrient(withExifOrientation(jpeg(200, 100), 6));
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(oriented));
        assertEquals(100, image.getWidth(), "orientation=6 应旋转为竖版");
        assertEquals(200, image.getHeight());
    }

    @Test
    void autoOrient180KeepsDimensions() throws Exception {
        byte[] oriented = ImageUtil.autoOrient(withExifOrientation(jpeg(200, 100), 3));
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(oriented));
        assertEquals(200, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    @Test
    void passthroughWithoutExif() throws Exception {
        byte[] png = pngBytes();
        assertArrayEquals(png, ImageUtil.autoOrient(png));
        byte[] plainJpeg = jpeg(120, 80);
        assertArrayEquals(plainJpeg, ImageUtil.autoOrient(plainJpeg));
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
