package com.yingying.cuotiku.server.mini;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.ai.ImageUtil;
import com.yingying.cuotiku.server.entity.MediaAsset;
import java.io.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/** 毫米声明式布局按CONTAIN放置题图。PDF使用打印任务的固定资产和固定模板快照。 */
@Component
public class MiniPdfRenderer {
  private final MiniSupport s;
  private final MiniAssetService assets;

  public MiniPdfRenderer(MiniSupport s, MiniAssetService assets) {
    this.s = s;
    this.assets = assets;
  }

  private static float pt(double mm) {
    return (float) (mm * 72 / 25.4);
  }

  public record Output(byte[] bytes, int pageCount) {}

  public Output render(MiniPrintService.RenderInput input) throws IOException {
    JsonNode layout = input.snapshot().path("layout"), slots = layout.path("slots");
    float width = pt(input.snapshot().path("paperWidthMm").asDouble()),
        height = pt(input.snapshot().path("paperHeightMm").asDouble());
    int count = slots.size();
    if (count < 1) throw new IOException("模板没有题目区域");
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (int copy = 0; copy < input.copies(); copy++)
        for (int start = 0; start < input.assetIds().size(); start += count) {
          PDPage page = new PDPage(new PDRectangle(width, height));
          doc.addPage(page);
          try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
            for (int slotIndex = 0;
                slotIndex < count && start + slotIndex < input.assetIds().size();
                slotIndex++) {
              JsonNode slot = slots.get(slotIndex);
              MediaAsset asset = s.get(MediaAsset.class, input.assetIds().get(start + slotIndex));
              PDImageXObject image =
                  PDImageXObject.createFromByteArray(
                      doc, ImageUtil.autoOrient(assets.bytes(asset)), "question");
              float slotW = pt(slot.path("width").asDouble()),
                  slotH = pt(slot.path("height").asDouble()),
                  scale = Math.min(slotW / image.getWidth(), slotH / image.getHeight()),
                  w = image.getWidth() * scale,
                  h = image.getHeight() * scale;
              float x = pt(slot.path("x").asDouble()) + (slotW - w) / 2,
                  y = height - pt(slot.path("y").asDouble()) - slotH + (slotH - h) / 2;
              stream.drawImage(image, x, y, w, h);
            }
          }
        }
      doc.save(out);
      return new Output(out.toByteArray(), doc.getNumberOfPages());
    }
  }
}
