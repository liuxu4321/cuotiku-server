package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 采集工作区与不可变图片版本。切换版本不覆盖原图，题框始终关联固定版本。 */
@Service
@Transactional
public class MiniCaptureService {
  private final MiniSupport s;
  private final MiniAssetService assets;
  private final MiniIdempotency receipts;

  public MiniCaptureService(MiniSupport s, MiniAssetService assets, MiniIdempotency receipts) {
    this.s = s;
    this.assets = assets;
    this.receipts = receipts;
  }

  public CaptureBatch batch(User user, String student, String id) {
    CaptureBatch row = s.owned(CaptureBatch.class, id, user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("批次不存在");
    return row;
  }

  public CapturePhoto photo(User user, String student, String id) {
    CapturePhoto row = s.owned(CapturePhoto.class, id, user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("照片不存在");
    batch(user, student, row.getBatchId());
    return row;
  }

  public ImageRevision revision(User user, String student, CapturePhoto photo, String id) {
    ImageRevision row = s.owned(ImageRevision.class, id, user, student);
    if (!row.getPhotoId().equals(photo.getId())) throw ApiException.notFound("图片版本不存在");
    assets.available(user, student, row.getAssetId());
    return row;
  }

  public Object create(User user, String student, JsonNode body) {
    s.activeStudent(user, student);
    return receipts.once(
        user,
        "CAPTURE",
        required(body, "clientRequestId", 64),
        map("studentId", student, "body", body),
        () -> {
          CaptureBatch row = new CaptureBatch();
          row.setUserId(user.getId());
          row.setStudentId(student);
          row.setClientRequestId(required(body, "clientRequestId", 64));
          row.setMode(oneOf(required(body, "mode", 16), "SINGLE", "MULTI"));
          row.setExpiresAt(Instant.now().plusSeconds(7 * 86400));
          s.save(row);
          return batchDto(row);
        });
  }

  @Transactional(readOnly = true)
  public Object list(User user, String student, Map<String, String> query) {
    s.student(user, student);
    return s.page(
        CaptureBatch.class,
        map("userId", user.getId(), "studentId", student, "deletedAt", null),
        query,
        "createdAt",
        true,
        this::batchDto);
  }

  @Transactional(readOnly = true)
  public Object get(User user, String student, String id) {
    CaptureBatch row = batch(user, student, id);
    return map("batch", batchDto(row), "photos", photos(row).stream().map(this::photoDto).toList());
  }

  public Object add(User user, String student, String id, JsonNode body) {
    s.activeStudent(user, student);
    return receipts.once(
        user,
        "CAPTURE_PHOTO",
        required(body, "clientRequestId", 64),
        map("studentId", student, "batchId", id, "body", body),
        () -> {
          CaptureBatch row = batch(user, student, id);
          s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
          if (!"DRAFT".equals(row.getStatus())) throw ApiException.conflict("拍摄批次已结束");
          List<CapturePhoto> existing = photos(row);
          if (existing.size() >= (row.getMode().equals("SINGLE") ? 1 : 30))
            throw ApiException.badRequest("超过本批次照片数量限制");
          MediaAsset asset = assets.available(user, student, required(body, "assetId", 36));
          if (!"ORIGINAL".equals(asset.getPurpose())) throw ApiException.badRequest("需要原图资产");
          CapturePhoto photo = new CapturePhoto();
          photo.setBatchId(id);
          photo.setUserId(user.getId());
          photo.setStudentId(student);
          photo.setOriginalAssetId(asset.getId());
          photo.setSortOrder(
              existing.stream().mapToInt(CapturePhoto::getSortOrder).max().orElse(-1) + 1);
          photo.setSource(oneOf(required(body, "source", 16), "CAMERA", "ALBUM"));
          String captured = text(body, "capturedAt");
          try {
            photo.setCapturedAt(captured == null ? null : Instant.parse(captured));
          } catch (Exception e) {
            throw ApiException.badRequest("capturedAt必须为ISO8601");
          }
          photo.setUploadedAt(Instant.now());
          s.save(photo);
          ImageRevision revision = new ImageRevision();
          revision.setUserId(user.getId());
          revision.setStudentId(student);
          revision.setPhotoId(photo.getId());
          revision.setAssetId(asset.getId());
          revision.setOperation("ORIGINAL");
          revision.setParametersJson(s.encode(map("schemaVersion", 1)));
          s.save(revision);
          photo.setCurrentRevisionId(revision.getId());
          return photoDto(photo);
        });
  }

  public Object finish(User user, String student, String id) {
    CaptureBatch row = batch(user, student, id);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    if (photos(row).isEmpty()) throw ApiException.badRequest("请先拍摄或导入照片");
    if ("DRAFT".equals(row.getStatus())) {
      row.setStatus("READY");
      row.setFinishedAt(Instant.now());
    }
    return batchDto(row);
  }

  public Object order(User user, String student, String id, JsonNode body) {
    CaptureBatch row = batch(user, student, id);
    s.em.lock(row, LockModeType.PESSIMISTIC_WRITE);
    List<JsonNode> ids = array(body, "photoIds", 30);
    List<CapturePhoto> photos = photos(row);
    if (ids.size() != photos.size()) throw ApiException.badRequest("需要完整照片顺序");
    int temporary = photos.stream().mapToInt(CapturePhoto::getSortOrder).max().orElse(0) + 1000;
    for (int i = 0; i < photos.size(); i++) photos.get(i).setSortOrder(temporary + i);
    s.em.flush();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < ids.size(); i++) {
      String photoId = ids.get(i).asText();
      if (!seen.add(photoId)) throw ApiException.badRequest("照片ID重复");
      CapturePhoto photo = photo(user, student, photoId);
      if (!photo.getBatchId().equals(id)) throw ApiException.notFound("照片不存在");
      photo.setSortOrder(i);
    }
    return map("photos", photos(row).stream().map(this::photoDto).toList());
  }

  public Object deletePhoto(User user, String student, String id) {
    CapturePhoto photo = photo(user, student, id);
    s.em.lock(batch(user, student, photo.getBatchId()), LockModeType.PESSIMISTIC_WRITE);
    int minimum =
        s.find(CapturePhoto.class, map("batchId", photo.getBatchId())).stream()
            .mapToInt(CapturePhoto::getSortOrder)
            .min()
            .orElse(0);
    photo.setSortOrder(Math.min(0, minimum) - 1);
    photo.setDeletedAt(Instant.now());
    return empty();
  }

  public Object deleteBatch(User user, String student, String id) {
    CaptureBatch row = batch(user, student, id);
    row.setDeletedAt(Instant.now());
    for (CapturePhoto photo : photos(row)) photo.setDeletedAt(Instant.now());
    return empty();
  }

  @Transactional(readOnly = true)
  public Object revisions(User user, String student, String id) {
    CapturePhoto photo = photo(user, student, id);
    List<ImageRevision> rows =
        s.find(
            ImageRevision.class, map("photoId", id, "userId", user.getId(), "studentId", student));
    rows.sort(Comparator.comparing(ImageRevision::getCreatedAt));
    return map(
        "items",
        rows.stream()
            .map(
                r ->
                    map(
                        "id",
                        r.getId(),
                        "photoId",
                        r.getPhotoId(),
                        "parentRevisionId",
                        r.getParentRevisionId(),
                        "asset",
                        assets.dto(assets.available(user, student, r.getAssetId())),
                        "operation",
                        r.getOperation(),
                        "parameters",
                        s.decode(r.getParametersJson()),
                        "createdAt",
                        r.getCreatedAt()))
            .toList(),
        "currentRevisionId",
        photo.getCurrentRevisionId(),
        "originalRevisionId",
        rows.stream()
            .filter(r -> "ORIGINAL".equals(r.getOperation()))
            .findFirst()
            .map(ImageRevision::getId)
            .orElse(null));
  }

  public Object selectRevision(User user, String student, String id, JsonNode body) {
    CapturePhoto photo = photo(user, student, id);
    s.em.lock(photo, LockModeType.PESSIMISTIC_WRITE);
    ImageRevision r = revision(user, student, photo, required(body, "revisionId", 36));
    photo.setCurrentRevisionId(r.getId());
    return photoDto(photo);
  }

  @Transactional(readOnly = true)
  public Object regions(User user, String student, String id, Map<String, String> query) {
    CapturePhoto photo = photo(user, student, id);
    String revisionId = query.getOrDefault("revisionId", photo.getCurrentRevisionId());
    revision(user, student, photo, revisionId);
    List<QuestionRegion> regions =
        s.find(
            QuestionRegion.class,
            map(
                "userId",
                user.getId(),
                "studentId",
                student,
                "photoId",
                id,
                "revisionId",
                revisionId,
                "deletedAt",
                null));
    regions.sort(Comparator.comparing(QuestionRegion::getSortOrder));
    return map("items", regions.stream().map(this::regionDto).toList());
  }

  public Object replaceRegions(User user, String student, String id, JsonNode body) {
    return receipts.once(
        user,
        "REGIONS",
        required(body, "clientRequestId", 64),
        map("studentId", student, "photoId", id, "body", body),
        () -> {
          CapturePhoto photo = photo(user, student, id);
          s.em.lock(photo, LockModeType.PESSIMISTIC_WRITE);
          String revisionId = required(body, "revisionId", 36);
          revision(user, student, photo, revisionId);
          JsonNode items = body.get("items");
          if (items == null || !items.isArray() || items.size() > 100)
            throw ApiException.badRequest("items为最多100项数组");
          List<QuestionRegion> old =
              s.find(
                  QuestionRegion.class,
                  map(
                      "userId",
                      user.getId(),
                      "studentId",
                      student,
                      "photoId",
                      id,
                      "revisionId",
                      revisionId,
                      "deletedAt",
                      null));
          for (QuestionRegion row : old) row.setDeletedAt(Instant.now());
          List<Object> result = new ArrayList<>();
          Set<String> seen = new HashSet<>();
          for (JsonNode item : items) {
            String clientId = required(item, "clientRegionId", 64);
            if (!seen.add(clientId)) throw ApiException.badRequest("题框标识重复");
            JsonNode geometry = item.get("geometry");
            geometry(geometry);
            QuestionRegion row = new QuestionRegion();
            row.setUserId(user.getId());
            row.setStudentId(student);
            row.setPhotoId(id);
            row.setRevisionId(revisionId);
            row.setClientRegionId(clientId);
            row.setGeometryJson(s.encode(geometry));
            row.setOrigin("MANUAL");
            row.setSortOrder(integer(item, "sortOrder", result.size(), 0, 1000));
            s.save(row);
            result.add(regionDto(row));
          }
          return map("items", result);
        });
  }

  public static void geometry(JsonNode g) {
    if (g == null
        || !g.isObject()
        || g.path("schemaVersion").asInt() != 1
        || !"RECTANGLE".equals(g.path("shape").asText())
        || !"NORMALIZED".equals(g.path("coordinateSpace").asText()))
      throw ApiException.badRequest("题框必须为schemaVersion=1的归一化矩形");
    for (String key : List.of("x", "y", "width", "height"))
      if (!g.path(key).isNumber() || !Double.isFinite(g.path(key).asDouble()))
        throw ApiException.badRequest("题框坐标无效");
    double x = g.path("x").asDouble(),
        y = g.path("y").asDouble(),
        w = g.path("width").asDouble(),
        h = g.path("height").asDouble();
    if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > 1.000001 || y + h > 1.000001)
      throw ApiException.badRequest("题框超出图片范围");
  }

  public List<CapturePhoto> photos(CaptureBatch row) {
    List<CapturePhoto> photos =
        s.find(
            CapturePhoto.class,
            map(
                "batchId",
                row.getId(),
                "userId",
                row.getUserId(),
                "studentId",
                row.getStudentId(),
                "deletedAt",
                null));
    photos.sort(
        Comparator.comparing(CapturePhoto::getSortOrder).thenComparing(CapturePhoto::getId));
    return photos;
  }

  public Object batchDto(CaptureBatch row) {
    return map(
        "id",
        row.getId(),
        "studentId",
        row.getStudentId(),
        "mode",
        row.getMode(),
        "status",
        row.getStatus(),
        "photoCount",
        photos(row).size(),
        "finishedAt",
        row.getFinishedAt(),
        "createdAt",
        row.getCreatedAt(),
        "updatedAt",
        row.getUpdatedAt());
  }

  public Object photoDto(CapturePhoto row) {
    return map(
        "id",
        row.getId(),
        "batchId",
        row.getBatchId(),
        "studentId",
        row.getStudentId(),
        "source",
        row.getSource(),
        "sortOrder",
        row.getSortOrder(),
        "originalAssetId",
        row.getOriginalAssetId(),
        "currentRevisionId",
        row.getCurrentRevisionId(),
        "uploadedAt",
        row.getUploadedAt());
  }

  public Object regionDto(QuestionRegion row) {
    return map(
        "id",
        row.getId(),
        "photoId",
        row.getPhotoId(),
        "revisionId",
        row.getRevisionId(),
        "cropAssetId",
        row.getCropAssetId(),
        "geometry",
        s.decode(row.getGeometryJson()),
        "sortOrder",
        row.getSortOrder(),
        "origin",
        row.getOrigin(),
        "status",
        row.getStatus());
  }
}
