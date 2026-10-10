package com.yingying.cuotiku.server.mini;

import com.yingying.cuotiku.server.entity.MediaAsset;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 本地调试文件传输；无账号Token但严格校验资源限定的短时文件凭证。 */
@RestController
@RequestMapping("/api/mini/v1/files")
public class MiniFileController {
  private final MiniAssetService assets;

  public MiniFileController(MiniAssetService assets) {
    this.assets = assets;
  }

  @PutMapping("/uploads/{uploadId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void upload(
      @PathVariable String uploadId, @RequestParam String ticket, HttpServletRequest request)
      throws IOException {
    assets.proxyUpload(uploadId, ticket, request.getContentType(), request.getInputStream());
  }

  @GetMapping("/assets/{assetId}")
  public ResponseEntity<byte[]> download(
      @PathVariable String assetId,
      @RequestParam String ticket,
      @RequestParam(defaultValue = "INLINE") String disposition) {
    MiniSupport.oneOf(disposition, "INLINE", "ATTACHMENT");
    MediaAsset asset = assets.downloadMetadata(assetId, ticket);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(asset.getMimeType()))
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            disposition.equals("ATTACHMENT")
                ? "attachment; filename=\"" + asset.getId() + "." + asset.getFormat() + "\""
                : "inline")
        .cacheControl(CacheControl.noStore())
        .body(assets.bytes(asset));
  }
}
