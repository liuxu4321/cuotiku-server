package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.ai.*;
import com.yingying.cuotiku.server.web.ApiException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** 腾讯云适配器。逐次调用审计，EXIF修正，擦除前缩放与RGB归一化。 */
@Component
public class MiniOcrGateway {
  private final TencentOcrClient client;
  private final MiniAiAuditService audit;

  public MiniOcrGateway(TencentOcrClient client, MiniAiAuditService audit) {
    this.client = client;
    this.audit = audit;
  }

  public record Result(byte[] bytes, List<Map<String, Object>> regions, List<Object> steps) {}

  private <T> T call(
      MiniProcessingService.ItemInput input,
      String action,
      byte[] bytes,
      Supplier<T> actionCall,
      java.util.function.Function<T, String> requestId,
      java.util.function.ToIntFunction<T> outputBytes) {
    for (int attempt = 1; attempt <= 2; attempt++) {
      long start = System.currentTimeMillis();
      try {
        T result = actionCall.get();
        audit.ocr(
            input.user(),
            input.studentId(),
            input.itemId(),
            input.assetId(),
            action,
            attempt,
            input.traceId(),
            requestId.apply(result),
            bytes.length,
            outputBytes.applyAsInt(result),
            System.currentTimeMillis() - start,
            null,
            input.parameters());
        return result;
      } catch (ApiException e) {
        audit.ocr(
            input.user(),
            input.studentId(),
            input.itemId(),
            input.assetId(),
            action,
            attempt,
            input.traceId(),
            null,
            bytes.length,
            0,
            System.currentTimeMillis() - start,
            e,
            input.parameters());
        if (attempt == 2 || !e.getMessage().contains("InternalError")) throw e;
      }
    }
    throw new IllegalStateException();
  }

  public Result process(MiniProcessingService.ItemInput input, byte[] original) {
    if (!input.user().isEnabled() || input.user().isCancelled())
      throw ApiException.forbidden("账号不可用");
    if (!input.user().isAiEnabled()) throw ApiException.forbidden("账号未开通AI权限");
    if (!client.configured()) throw new ApiException(503, "后台尚未配置腾讯云AI");
    byte[] work = ImageUtil.autoOrient(original);
    List<Object> steps = new ArrayList<>();
    List<Map<String, Object>> regions = new ArrayList<>();
    String op = input.operation();
    JsonNode params = input.parameters();
    boolean pipeline = op.equals("PAPER_PROCESS");
    boolean enhance =
        op.equals("CORRECT") || op.equals("ENHANCE") || (pipeline && bool(params, "enhance", true));
    boolean split = op.equals("SPLIT") || (pipeline && bool(params, "split", true));
    boolean erase = op.equals("ERASE") || (pipeline && bool(params, "erase", true));
    if (enhance) {
      byte[] bytes = work;
      int enhanceType = op.equals("ENHANCE") ? integer(params, "enhanceType", 2, -1, 6) : -1;
      try {
        TencentOcrClient.CropEnhanceResult result =
            call(
                input,
                "CropEnhanceImageOCR",
                bytes,
                () ->
                    client.cropEnhance(
                        Base64.getEncoder().encodeToString(bytes),
                        bool(params, "crop", true),
                        bool(params, "deskew", true),
                        bool(params, "adjustOrientation", false),
                        false,
                        enhanceType),
                TencentOcrClient.CropEnhanceResult::requestId,
                r -> r.imageBytes() == null ? 0 : r.imageBytes().length);
        if (result.imageBytes() == null || result.imageBytes().length == 0)
          throw new ApiException(502, "切边增强未返回图片");
        work = result.imageBytes();
        steps.add(
            map("operation", "ENHANCE", "status", "SUCCEEDED", "requestId", result.requestId()));
      } catch (ApiException e) {
        if (!pipeline) throw e;
        steps.add(
            map(
                "operation",
                "ENHANCE",
                "status",
                "FAILED",
                "fallback",
                "ORIGINAL",
                "code",
                e.getCode()));
      }
    }
    if (split) {
      byte[] bytes = work;
      TencentOcrClient.SplitResult result =
          call(
              input,
              "QuestionSplitLayoutOCR",
              bytes,
              () ->
                  client.splitQuestions(
                      Base64.getEncoder().encodeToString(bytes), bool(params, "useNewModel", true)),
              TencentOcrClient.SplitResult::requestId,
              r -> 0);
      for (TencentOcrClient.RawBox b : result.boxes()) {
        double x = Math.max(0, b.minX() / result.width()),
            y = Math.max(0, b.minY() / result.height()),
            w = Math.min(1 - x, (b.maxX() - b.minX()) / result.width()),
            h = Math.min(1 - y, (b.maxY() - b.minY()) / result.height());
        if (w > 0 && h > 0)
          regions.add(
              map(
                  "schemaVersion",
                  1,
                  "shape",
                  "RECTANGLE",
                  "coordinateSpace",
                  "NORMALIZED",
                  "x",
                  x,
                  "y",
                  y,
                  "width",
                  w,
                  "height",
                  h));
      }
      steps.add(map("operation", "SPLIT", "status", "SUCCEEDED", "requestId", result.requestId()));
    }
    if (erase) {
      byte[] bytes = normalizeErase(work);
      TencentOcrClient.EraseResult result =
          call(
              input,
              "EraseHandwrittenImageOCR",
              bytes,
              () -> client.erase(Base64.getEncoder().encodeToString(bytes)),
              TencentOcrClient.EraseResult::requestId,
              r -> r.imageBase64().length() * 3 / 4);
      byte[] output =
          Base64.getDecoder()
              .decode(result.imageBase64().replaceFirst("^data:image/[^;]+;base64,", ""));
      if (split) assertSameRatio(work, output);
      work = output;
      steps.add(map("operation", "ERASE", "status", "SUCCEEDED", "requestId", result.requestId()));
    }
    return new Result(op.equals("SPLIT") ? null : work, regions, steps);
  }

  private byte[] normalizeErase(byte[] bytes) {
    try {
      BufferedImage input = ImageIO.read(new ByteArrayInputStream(ImageUtil.autoOrient(bytes)));
      double scale = Math.min(1, 4096.0 / Math.max(input.getWidth(), input.getHeight()));
      int w = Math.max(1, (int) Math.round(input.getWidth() * scale)),
          h = Math.max(1, (int) Math.round(input.getHeight() * scale));
      BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = rgb.createGraphics();
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, w, h);
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.drawImage(input, 0, 0, w, h, null);
      graphics.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(rgb, "jpg", out);
      return out.toByteArray();
    } catch (Exception e) {
      throw ApiException.badRequest("擦除输入图片无效");
    }
  }

  private void assertSameRatio(byte[] before, byte[] after) {
    try {
      BufferedImage a = ImageIO.read(new ByteArrayInputStream(before)),
          b = ImageIO.read(new ByteArrayInputStream(after));
      if (a == null
          || b == null
          || Math.abs((double) a.getWidth() / a.getHeight() - (double) b.getWidth() / b.getHeight())
              > .01) throw new IOException();
    } catch (IOException e) {
      throw new ApiException(502, "擦除改变图片几何，请重新检测题框");
    }
  }
}
