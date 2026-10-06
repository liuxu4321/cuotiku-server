package com.yingying.cuotiku.server.ai;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 图片方向工具：手机照片像素按传感器方向存储，显示方向记录在 EXIF Orientation 中。
 * ImageIO 解码与腾讯云引擎输出均不保留 EXIF，若不先按 Orientation 旋转像素，
 * 处理结果会相对原图旋转 90/180/270 度。本工具在服务端统一"像素归正"。
 */
public final class ImageUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    private ImageUtil() {
    }

    public static int readExifOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null && dir.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return dir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("[图片方向] 读取EXIF失败，按正常方向处理: {}", e.getMessage());
        }
        return 1;
    }

    /** 按 EXIF Orientation 旋转像素并重编码；方向正常时原样返回。 */
    public static byte[] autoOrient(byte[] bytes) {
        int orientation = readExifOrientation(bytes);
        if (orientation <= 1 || orientation > 8) {
            return bytes;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                return bytes;
            }
            int w = source.getWidth();
            int h = source.getHeight();
            boolean swap = orientation >= 5;
            int tw = swap ? h : w;
            int th = swap ? w : h;
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, tw, th);
            AffineTransform t = new AffineTransform();
            switch (orientation) {
                case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);
                case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);
                case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);
                case 5 -> t = new AffineTransform(0, 1, 1, 0, 0, 0);
                case 6 -> t = new AffineTransform(0, 1, -1, 0, h, 0);
                case 7 -> t = new AffineTransform(0, -1, -1, 0, h, w);
                case 8 -> t = new AffineTransform(0, -1, 1, 0, 0, w);
                default -> { }
            }
            g.drawImage(source, t, null);
            g.dispose();
            boolean png = isPng(bytes);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, png ? "png" : "jpg", bos);
            log.debug("[图片方向] 已按EXIF orientation={} 旋转像素 {}x{} → {}x{}", orientation, w, h, tw, th);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("[图片方向] 自动旋转失败，按原图处理: {}", e.getMessage());
            return bytes;
        }
    }

    public static boolean isPng(byte[] bytes) {
        return bytes.length > 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }
}
