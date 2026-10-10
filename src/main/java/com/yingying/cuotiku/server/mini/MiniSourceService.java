package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.ai.ImageUtil;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 保存、草稿和打印共用来源解析，临时URL或客户端对象键不能作为可信题目来源。 */
@Service
@Transactional
public class MiniSourceService {
  private final MiniSupport s;
  private final MiniCaptureService captures;
  private final MiniAssetService assets;

  public MiniSourceService(MiniSupport s, MiniCaptureService captures, MiniAssetService assets) {
    this.s = s;
    this.captures = captures;
    this.assets = assets;
  }

  public record Source(
      String type,
      String id,
      String photoId,
      String revisionId,
      MediaAsset asset,
      Map<String, Object> content) {}

  public BookEntry entry(User user, String student, String id) {
    BookEntry row = s.owned(BookEntry.class, id, user, student);
    if (row.getDeletedAt() != null) throw ApiException.notFound("错题不存在");
    return row;
  }

  public Source resolve(User user, String student, JsonNode source) {
    s.activeStudent(user, student);
    String type = oneOf(required(source, "sourceType", 16), "ENTRY", "REGION", "PHOTO"),
        id = required(source, "sourceId", 36);
    String revisionId = text(source, "inputRevisionId");
    if (type.equals("ENTRY")) {
      BookEntry entry = entry(user, student, id);
      if (revisionId != null && !Objects.equals(revisionId, entry.getImageRevisionId()))
        throw ApiException.conflict("错题图版本已变化");
      MediaAsset asset = assets.available(user, student, entry.getImageAssetId());
      return new Source(
          type,
          id,
          entry.getSourcePhotoId(),
          entry.getImageRevisionId(),
          asset,
          map(
              "entryId",
              id,
              "version",
              entry.getVersion(),
              "subject",
              classification(UserSubject.class, entry.getSubjectId()),
              "topic",
              classification(SubjectTopic.class, entry.getTopicId()),
              "errorType",
              classification(ErrorType.class, entry.getErrorTypeId()),
              "answer",
              entry.getAnswer(),
              "remark",
              entry.getRemark()));
    }
    if (type.equals("PHOTO")) {
      CapturePhoto photo = captures.photo(user, student, id);
      ImageRevision revision =
          captures.revision(user, student, photo, required(source, "inputRevisionId", 36));
      return new Source(
          type,
          id,
          id,
          revision.getId(),
          assets.available(user, student, revision.getAssetId()),
          map("photoId", id, "revisionId", revision.getId()));
    }
    QuestionRegion region = s.owned(QuestionRegion.class, id, user, student);
    if (region.getDeletedAt() != null || !"ACTIVE".equals(region.getStatus()))
      throw ApiException.notFound("题框不存在");
    CapturePhoto photo = captures.photo(user, student, region.getPhotoId());
    ImageRevision revision = captures.revision(user, student, photo, region.getRevisionId());
    if (revisionId != null && !revisionId.equals(region.getRevisionId()))
      throw ApiException.conflict("题框版本不匹配");
    MediaAsset crop;
    if (region.getCropAssetId() != null)
      crop = assets.available(user, student, region.getCropAssetId());
    else {
      MediaAsset input = assets.available(user, student, revision.getAssetId());
      byte[] bytes = assets.bytes(input);
      JsonNode geometry = s.decode(region.getGeometryJson());
      MiniCaptureService.geometry(geometry);
      try {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(ImageUtil.autoOrient(bytes)));
        int x = (int) Math.floor(geometry.path("x").asDouble() * image.getWidth()),
            y = (int) Math.floor(geometry.path("y").asDouble() * image.getHeight());
        int
            width =
                Math.min(
                    image.getWidth() - x,
                    Math.max(
                        1, (int) Math.round(geometry.path("width").asDouble() * image.getWidth()))),
            height =
                Math.min(
                    image.getHeight() - y,
                    Math.max(
                        1,
                        (int) Math.round(geometry.path("height").asDouble() * image.getHeight())));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image.getSubimage(x, y, width, height), "png", out);
        crop = assets.store(user, student, out.toByteArray(), "QUESTION_CROP", "image/png");
        region.setCropAssetId(crop.getId());
      } catch (IOException | IllegalArgumentException e) {
        throw ApiException.badRequest("题框裁剪失败，请重新框选");
      }
    }
    return new Source(
        type,
        id,
        photo.getId(),
        revision.getId(),
        crop,
        map(
            "regionId",
            id,
            "photoId",
            photo.getId(),
            "revisionId",
            revision.getId(),
            "geometry",
            s.decode(region.getGeometryJson())));
  }

  public Object classification(Class<?> type, Object id) {
    if (id == null) return null;
    Object row = s.get(type, id);
    if (row instanceof UserSubject r)
      return map("id", r.getId().toString(), "name", r.getName(), "status", r.getStatus());
    if (row instanceof SubjectTopic r)
      return map("id", r.getId().toString(), "name", r.getName(), "status", r.getStatus());
    ErrorType r = (ErrorType) row;
    return map("id", r.getId(), "name", r.getName(), "status", r.getStatus());
  }

  public Map<String, Object> entryDto(BookEntry r) {
    return map(
        "id",
        r.getId(),
        "studentId",
        r.getStudentId(),
        "grade",
        r.getGrade(),
        "term",
        r.getTerm(),
        "subject",
        classification(UserSubject.class, r.getSubjectId()),
        "topic",
        classification(SubjectTopic.class, r.getTopicId()),
        "errorType",
        classification(ErrorType.class, r.getErrorTypeId()),
        "imageAssetId",
        r.getImageAssetId(),
        "thumbnailAssetId",
        r.getThumbnailAssetId(),
        "answer",
        r.getAnswer(),
        "remark",
        r.getRemark(),
        "masteryStatus",
        r.getMasteryStatus(),
        "practiceCount",
        r.getPracticeCount(),
        "correctCount",
        r.getCorrectCount() == null ? 0 : r.getCorrectCount(),
        "lastPracticedAt",
        r.getLastPracticedAt(),
        "version",
        r.getVersion(),
        "createdAt",
        r.getCreatedAt(),
        "updatedAt",
        r.getUpdatedAt());
  }
}
