package com.yingying.cuotiku.server.mini;

import static com.yingying.cuotiku.server.mini.MiniSupport.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学生资料及首页摘要。账号可拥有多个孩子，学生不会跨账号共享。 */
@Service
@Transactional
public class MiniStudentService {
  private final MiniSupport s;
  private final MiniSourceService entryDto;

  public MiniStudentService(MiniSupport s, MiniSourceService entryDto) {
    this.s = s;
    this.entryDto = entryDto;
  }

  @Transactional(readOnly = true)
  public Object list(User user, Map<String, String> query) {
    Map<String, Object> filters = map("userId", user.getId(), "deletedAt", null);
    if (query.containsKey("status"))
      filters.put("status", oneOf(query.get("status"), "ACTIVE", "ARCHIVED"));
    return s.page(StudentProfile.class, filters, query, "sortOrder", false, this::dto);
  }

  public Object create(User user, JsonNode body) {
    StudentProfile student = new StudentProfile();
    student.setUserId(user.getId());
    student.setNickname(required(body, "nickname", 64));
    student.setGrade(integer(body, "grade", -1, 1, 12));
    if (student.getGrade() < 1) throw ApiException.badRequest("grade必填");
    student.setTerm(integer(body, "term", 1, 1, 2));
    student.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    avatar(user, body, student);
    s.save(student);
    return dto(student);
  }

  @Transactional(readOnly = true)
  public Object get(User user, String id) {
    return dto(s.student(user, id));
  }

  public Object update(User user, String id, JsonNode body) {
    StudentProfile student = s.student(user, id);
    if (body.has("nickname")) student.setNickname(required(body, "nickname", 64));
    if (body.has("grade")) student.setGrade(integer(body, "grade", -1, 1, 12));
    if (body.has("term")) student.setTerm(integer(body, "term", -1, 1, 2));
    if (body.has("sortOrder")) student.setSortOrder(integer(body, "sortOrder", 0, 0, 100000));
    if (body.has("status"))
      student.setStatus(oneOf(required(body, "status", 16), "ACTIVE", "ARCHIVED"));
    avatar(user, body, student);
    s.em.flush();
    return dto(student);
  }

  private void avatar(User user, JsonNode body, StudentProfile student) {
    if (!body.has("avatarAssetId")) return;
    String id = text(body, "avatarAssetId");
    if (id != null) {
      MediaAsset asset = s.get(MediaAsset.class, id);
      if (!user.getId().equals(asset.getUserId())
          || !"AVAILABLE".equals(asset.getStatus())
          || !"AVATAR".equals(asset.getPurpose())
          || (asset.getStudentId() != null && !asset.getStudentId().equals(student.getId())))
        throw ApiException.notFound("头像资产不存在");
    }
    student.setAvatarAssetId(id);
  }

  public Object current(User user, JsonNode body) {
    StudentProfile student = s.activeStudent(user, required(body, "studentId", 36));
    UserProfile profile = s.em.find(UserProfile.class, user.getId());
    if (profile == null) {
      profile = new UserProfile();
      profile.setUserId(user.getId());
      s.em.persist(profile);
    }
    profile.setLastStudentId(student.getId());
    return map("lastStudentId", student.getId());
  }

  @Transactional(readOnly = true)
  public Object summary(User user, String id, Map<String, String> query) {
    s.student(user, id);
    Map<String, Object> filters = map("userId", user.getId(), "studentId", id, "deletedAt", null);
    long total = s.count(BookEntry.class, filters);
    filters.put("masteryStatus", "MASTERED");
    long mastered = s.count(BookEntry.class, filters);
    int limit = 10;
    try {
      if (query.containsKey("recentLimit")) limit = Integer.parseInt(query.get("recentLimit"));
    } catch (Exception e) {
      throw ApiException.badRequest("recentLimit格式错误");
    }
    if (limit < 1 || limit > 20) throw ApiException.badRequest("recentLimit范围1～20");
    Map<String, Object> recent =
        s.page(
            BookEntry.class,
            map("userId", user.getId(), "studentId", id, "deletedAt", null),
            Map.of("limit", String.valueOf(limit)),
            "createdAt",
            true,
            entryDto::entryDto);
    return map(
        "student",
        dto(s.student(user, id)),
        "entryCount",
        total,
        "recentEntries",
        recent.get("items"),
        "pendingCount",
        total - mastered,
        "masteredCount",
        mastered);
  }

  public Map<String, Object> dto(StudentProfile value) {
    return map(
        "id",
        value.getId(),
        "nickname",
        value.getNickname(),
        "avatarAssetId",
        value.getAvatarAssetId(),
        "grade",
        value.getGrade(),
        "term",
        value.getTerm(),
        "status",
        value.getStatus(),
        "sortOrder",
        value.getSortOrder(),
        "createdAt",
        value.getCreatedAt(),
        "updatedAt",
        value.getUpdatedAt());
  }
}
