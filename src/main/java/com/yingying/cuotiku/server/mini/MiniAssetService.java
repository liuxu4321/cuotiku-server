package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.security.JwtService;
import com.yingying.cuotiku.server.storage.BookStorage;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 资产服务：COS签名直传与本地调试代理，完成前核对大小、摘要与实际图片格式。 */
@Service
@Transactional
public class MiniAssetService {
  public static final int MAX_BYTES = 20 * 1024 * 1024;
  private final MiniSupport s;
  private final MiniIdempotency receipts;
  final BookStorage storage;
  private final AppProperties properties;
  private final JwtService jwt;

  public MiniAssetService(
      MiniSupport s,
      MiniIdempotency receipts,
      BookStorage storage,
      AppProperties properties,
      JwtService jwt) {
    this.s = s;
    this.receipts = receipts;
    this.storage = storage;
    this.properties = properties;
    this.jwt = jwt;
  }

  public Object upload(User user, String student, JsonNode body) {
    s.activeStudent(user, student);
    String key = required(body, "clientRequestId", 64);
    return receipts.once(
        user,
        "UPLOAD",
        key,
        map("studentId", student, "body", body),
        () -> {
          String mime = oneOf(required(body, "mimeType", 128), "image/png", "image/jpeg");
          int size = integer(body, "sizeBytes", -1, 1, MAX_BYTES);
          if (size < 1) throw ApiException.badRequest("sizeBytes必填");
          String checksum = checksum(body);
          MediaAsset asset = new MediaAsset();
          asset.setUserId(user.getId());
          asset.setStudentId(student);
          asset.setPurpose(oneOf(required(body, "purpose", 32), "ORIGINAL", "AVATAR"));
          asset.setMimeType(mime);
          asset.setFormat(mime.equals("image/png") ? "png" : "jpg");
          location(asset);
          s.save(asset);
          AssetUploadSession upload = new AssetUploadSession();
          upload.setAssetId(asset.getId());
          upload.setStagingObjectKey(asset.getObjectKey());
          upload.setUserId(user.getId());
          upload.setStudentId(student);
          upload.setClientRequestId(key);
          upload.setExpectedSizeBytes((long) size);
          upload.setExpectedMimeType(mime);
          upload.setExpectedChecksum(checksum);
          upload.setExpiresAt(Instant.now().plusSeconds(300));
          s.save(upload);
          return uploadDto(upload, asset);
        });
  }

  private String checksum(JsonNode body) {
    String checksum = text(body, "checksumSha256");
    if (checksum != null && !checksum.matches("[a-fA-F0-9]{64}"))
      throw ApiException.badRequest("SHA256格式错误");
    return checksum == null ? null : checksum.toLowerCase(Locale.ROOT);
  }

  private void location(MediaAsset asset) {
    boolean cos = properties.cos() != null && properties.cos().cosEnabled();
    asset.setStorageProvider(cos ? "COS" : "LOCAL");
    asset.setBucket(cos ? properties.cos().bucket() : "local");
    asset.setRegion(cos ? properties.cos().region() : "local");
    asset.setObjectKey("mini/" + UUID.randomUUID() + "." + asset.getFormat());
  }

  private Object uploadDto(AssetUploadSession row, MediaAsset asset) {
    String url =
        storage.signedUrl(asset.getObjectKey(), "PUT", asset.getMimeType(), row.getExpiresAt());
    if (url == null)
      url =
          localUrl(
              "uploads/" + row.getId(), jwt.issueMiniResource(row.getId(), "mini_upload").token());
    return map(
        "uploadId",
        row.getId(),
        "assetId",
        asset.getId(),
        "expiresAt",
        row.getExpiresAt(),
        "upload",
        map(
            "method",
            "PUT",
            "url",
            url,
            "headers",
            map("Content-Type", asset.getMimeType()),
            "fields",
            empty()));
  }

  private String localUrl(String path, String ticket) {
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/api/mini/v1/files/")
        .path(path)
        .queryParam("ticket", ticket)
        .toUriString();
  }

  public Object complete(User user, String student, String uploadId, JsonNode body) {
    AssetUploadSession upload = s.owned(AssetUploadSession.class, uploadId, user, student);
    s.em.lock(upload, LockModeType.PESSIMISTIC_WRITE);
    MediaAsset asset = s.owned(MediaAsset.class, upload.getAssetId(), user, student);
    if ("COMPLETED".equals(upload.getStatus())) return dto(asset);
    if (!"ISSUED".equals(upload.getStatus()) || upload.getExpiresAt().isBefore(Instant.now()))
      throw new ApiException(410, "上传会话已过期，请重新上传");
    if (storage.size(asset.getObjectKey()) != upload.getExpectedSizeBytes())
      throw ApiException.badRequest("上传文件大小不匹配");
    byte[] bytes = storage.get(asset.getObjectKey());
    String hash = sha256(bytes);
    String declared = checksum(body);
    if ((declared != null && !declared.equals(hash))
        || (upload.getExpectedChecksum() != null && !upload.getExpectedChecksum().equals(hash)))
      throw ApiException.badRequest("上传文件摘要不匹配");
    imageMetadata(asset, bytes);
    asset.setSizeBytes((long) bytes.length);
    asset.setChecksumSha256(hash); // 转存到不可变对象键，旧PUT签名重放只能影响暂存文件。
    location(asset);
    storage.put(asset.getObjectKey(), bytes, asset.getMimeType());
    asset.setStatus("AVAILABLE");
    upload.setStatus("COMPLETED");
    upload.setCompletedAt(Instant.now());
    return dto(asset);
  }

  public void proxyUpload(String uploadId, String ticket, String mime, InputStream stream)
      throws IOException {
    verify(ticket, uploadId, "mini_upload");
    AssetUploadSession row = s.get(AssetUploadSession.class, uploadId);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    if (!"ISSUED".equals(row.getStatus()) || row.getExpiresAt().isBefore(Instant.now()))
      throw new ApiException(410, "上传会话已结束或过期");
    if (!row.getExpectedMimeType().equals(mime)) throw ApiException.badRequest("文件类型不匹配");
    byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
    if (bytes.length != row.getExpectedSizeBytes() || bytes.length > MAX_BYTES)
      throw ApiException.badRequest("文件大小不匹配");
    MediaAsset asset = s.get(MediaAsset.class, row.getAssetId());
    storage.put(row.getStagingObjectKey(), bytes, mime);
  }

  @Transactional(readOnly = true)
  public Object get(User user, String student, String id) {
    return dto(available(user, student, id));
  }

  public MediaAsset available(User user, String student, String id) {
    MediaAsset row = s.owned(MediaAsset.class, id, user, student);
    s.em.lock(row, LockModeType.PESSIMISTIC_READ);
    if (!"AVAILABLE".equals(row.getStatus()) || row.getDeletedAt() != null)
      throw ApiException.notFound("文件不可用");
    return row;
  }

  @Transactional(readOnly = true)
  public Object access(User user, String student, String id, JsonNode body) {
    String disposition =
        oneOf(
            Optional.ofNullable(text(body, "disposition")).orElse("INLINE"),
            "INLINE",
            "ATTACHMENT");
    return access(available(user, student, id), disposition);
  }

  public Object access(MediaAsset asset) {
    return access(asset, "INLINE");
  }

  public Object access(MediaAsset asset, String disposition) {
    Instant until = Instant.now().plusSeconds(300);
    String url =
        storage.signedUrl(
            asset.getObjectKey(),
            "GET",
            asset.getMimeType(),
            until,
            disposition.toLowerCase(Locale.ROOT));
    if (url == null)
      url =
          localUrl(
                  "assets/" + asset.getId(),
                  jwt.issueMiniResource(asset.getId(), "mini_download").token())
              + "&disposition="
              + disposition;
    return map("assetId", asset.getId(), "url", url, "expiresAt", until);
  }

  @Transactional(readOnly = true)
  public MediaAsset downloadMetadata(String id, String ticket) {
    verify(ticket, id, "mini_download");
    MediaAsset asset = s.get(MediaAsset.class, id);
    if (!"AVAILABLE".equals(asset.getStatus()) || asset.getDeletedAt() != null)
      throw ApiException.notFound("文件不存在");
    return asset;
  }

  private void verify(String ticket, String id, String kind) {
    try {
      var claims = jwt.parse(ticket);
      if (!id.equals(claims.getSubject()) || !kind.equals(claims.get("typ", String.class)))
        throw new IllegalArgumentException();
    } catch (Exception e) {
      throw ApiException.unauthorized("文件凭证无效或过期");
    }
  }

  public byte[] bytes(MediaAsset asset) {
    if (storage.size(asset.getObjectKey()) > MAX_BYTES) throw ApiException.badRequest("文件过大");
    return storage.get(asset.getObjectKey());
  }

  public MediaAsset store(User user, String student, byte[] bytes, String purpose, String mime) {
    if (bytes.length > MAX_BYTES) throw ApiException.badRequest("输出文件超过大小限制");
    MediaAsset asset = new MediaAsset();
    asset.setUserId(user == null ? null : user.getId());
    asset.setStudentId(student);
    asset.setPurpose(purpose);
    asset.setMimeType(mime);
    asset.setFormat(
        mime.equals("application/pdf")
            ? "pdf"
            : mime.equals("image/svg+xml") ? "svg" : mime.equals("image/jpeg") ? "jpg" : "png");
    location(asset);
    asset.setSizeBytes((long) bytes.length);
    asset.setChecksumSha256(sha256(bytes));
    if (mime.equals("image/png") || mime.equals("image/jpeg")) imageMetadata(asset, bytes);
    storage.put(asset.getObjectKey(), bytes, mime);
    asset.setStatus("AVAILABLE");
    return s.save(asset);
  }

  private void imageMetadata(MediaAsset asset, byte[] bytes) {
    try (ImageInputStream stream =
        ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw ApiException.badRequest("文件不是有效图片");
      ImageReader reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!List.of("png", "jpeg", "jpg").contains(format)
            || (!asset.getMimeType().equals("image/png") && format.equals("png"))
            || (asset.getMimeType().equals("image/png") && !format.equals("png")))
          throw ApiException.badRequest("图片实际格式与声明不一致");
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > 24000000L)
          throw ApiException.badRequest("图片像素超过限制");
        asset.setWidth(width);
        asset.setHeight(height);
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw ApiException.badRequest("图片内容损坏");
    }
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> dto(MediaAsset row) {
    return map(
        "id",
        row.getId(),
        "purpose",
        row.getPurpose(),
        "mimeType",
        row.getMimeType(),
        "width",
        row.getWidth(),
        "height",
        row.getHeight(),
        "sizeBytes",
        row.getSizeBytes(),
        "checksumSha256",
        row.getChecksumSha256(),
        "status",
        row.getStatus());
  }
}
