package com.yingying.cuotiku.server.mini;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.web.ApiException;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 小程序公共约束：显式学生归属、白名单DTO、绑定筛选的签名游标。 */
@Component
public class MiniSupport {
  @PersistenceContext EntityManager em;
  final ObjectMapper json;
  private final byte[] cursorKey;

  public MiniSupport(ObjectMapper json, AppProperties properties) {
    this.json = json;
    this.cursorKey = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
  }

  public static Map<String, Object> map(Object... values) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) out.put((String) values[i], values[i + 1]);
    return out;
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public JsonNode decode(String value) {
    try {
      return value == null ? json.createObjectNode() : json.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("持久化JSON损坏", e);
    }
  }

  public String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      encode(canonical(json.valueToTree(value))).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode canonical(JsonNode node) {
    if (node.isObject()) {
      ObjectNode sorted = json.createObjectNode();
      List<String> names = new ArrayList<>();
      node.fieldNames().forEachRemaining(names::add);
      Collections.sort(names);
      for (String name : names) sorted.set(name, canonical(node.get(name)));
      return sorted;
    }
    if (node.isArray()) {
      var array = json.createArrayNode();
      node.forEach(item -> array.add(canonical(item)));
      return array;
    }
    return node;
  }

  public static String text(JsonNode body, String key) {
    JsonNode v = body == null ? null : body.get(key);
    if (v == null || v.isNull()) return null;
    if (!v.isTextual()) throw ApiException.badRequest(key + "必须是字符串");
    return v.asText();
  }

  public static String required(JsonNode body, String key, int max) {
    String value = text(body, key);
    if (value == null || value.isBlank() || value.length() > max)
      throw ApiException.badRequest(key + "不能为空且最长" + max + "字符");
    return value.trim();
  }

  public static int integer(JsonNode body, String key, int fallback, int min, int max) {
    JsonNode v = body == null ? null : body.get(key);
    if (v == null) return fallback;
    if (!v.isIntegralNumber() || !v.canConvertToInt() || v.intValue() < min || v.intValue() > max)
      throw ApiException.badRequest(key + "范围必须为" + min + "～" + max);
    return v.intValue();
  }

  public static boolean bool(JsonNode body, String key, boolean fallback) {
    JsonNode v = body == null ? null : body.get(key);
    if (v == null) return fallback;
    if (!v.isBoolean()) throw ApiException.badRequest(key + "必须是boolean");
    return v.booleanValue();
  }

  public static long longId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id < 1) throw new NumberFormatException();
      return id;
    } catch (Exception e) {
      throw ApiException.badRequest("ID格式不正确");
    }
  }

  public static String oneOf(String value, String... allowed) {
    if (!List.of(allowed).contains(value))
      throw ApiException.badRequest("取值必须为" + String.join("/", allowed));
    return value;
  }

  public static List<JsonNode> array(JsonNode body, String key, int max) {
    JsonNode value = body.get(key);
    if (value == null || !value.isArray() || value.isEmpty() || value.size() > max)
      throw ApiException.badRequest(key + "必须为1～" + max + "项数组");
    List<JsonNode> list = new ArrayList<>();
    value.forEach(list::add);
    return list;
  }

  public <T> T save(T value) {
    em.persist(value);
    em.flush();
    return value;
  }

  public <T> T get(Class<T> type, Object id) {
    T value = em.find(type, id);
    if (value == null) throw ApiException.notFound("资源不存在");
    return value;
  }

  public StudentProfile student(User user, String id) {
    StudentProfile s = get(StudentProfile.class, id);
    if (!s.getUserId().equals(user.getId()) || s.getDeletedAt() != null)
      throw ApiException.notFound("学生不存在");
    return s;
  }

  public StudentProfile activeStudent(User user, String id) {
    StudentProfile s = student(user, id);
    if (!"ACTIVE".equals(s.getStatus())) throw ApiException.conflict("学生已归档");
    return s;
  }

  public <T> T owned(Class<T> type, Object id, User user, String studentId) {
    student(user, studentId);
    List<T> result = find(type, map("id", id, "userId", user.getId(), "studentId", studentId));
    if (result.isEmpty()) throw ApiException.notFound("资源不存在");
    return result.get(0);
  }

  /** 查询只接受服务端构造字段，客户端无法拼接JPQL。 */
  public <T> List<T> find(Class<T> type, Map<String, Object> filters) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<T> query = cb.createQuery(type);
    Root<T> root = query.from(type);
    query.where(predicates(cb, root, filters).toArray(Predicate[]::new));
    return em.createQuery(query).getResultList();
  }

  public <T> long count(Class<T> type, Map<String, Object> filters) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<T> root = query.from(type);
    query.select(cb.count(root)).where(predicates(cb, root, filters).toArray(Predicate[]::new));
    return em.createQuery(query).getSingleResult();
  }

  private List<Predicate> predicates(
      CriteriaBuilder cb, Root<?> root, Map<String, Object> filters) {
    List<Predicate> predicates = new ArrayList<>();
    filters.forEach(
        (field, value) ->
            predicates.add(
                value == null ? cb.isNull(root.get(field)) : cb.equal(root.get(field), value)));
    return predicates;
  }

  public <T> Map<String, Object> page(
      Class<T> type,
      Map<String, Object> filters,
      Map<String, String> params,
      String sortField,
      boolean descending,
      Function<T, Object> dto) {
    int limit = 20;
    try {
      if (params.containsKey("limit")) limit = Integer.parseInt(params.get("limit"));
    } catch (Exception e) {
      throw ApiException.badRequest("limit格式错误");
    }
    if (limit < 1 || limit > 100) throw ApiException.badRequest("limit范围1～100");
    Map<String, String> bound = new TreeMap<>(params);
    bound.remove("cursor");
    bound.remove("limit");
    String scope = hash(map("type", type.getSimpleName(), "filters", filters, "params", bound));
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<T> query = cb.createQuery(type);
    Root<T> root = query.from(type);
    List<Predicate> predicates = predicates(cb, root, filters);
    if (type == User.class && params.containsKey("phone"))
      predicates.add(
          cb.like(
              root.get("phone"),
              "%" + params.get("phone").replace("%", "\\%").replace("_", "\\_") + "%",
              '\\'));
    if (type == User.class && params.containsKey("memberActive")) {
      String active = oneOf(params.get("memberActive"), "true", "false");
      predicates.add(
          active.equals("true")
              ? cb.greaterThanOrEqualTo(root.get("memberExpireAt"), LocalDate.now())
              : cb.or(
                  cb.isNull(root.get("memberExpireAt")),
                  cb.lessThan(root.get("memberExpireAt"), LocalDate.now())));
    }
    if (type == BookEntry.class && params.containsKey("topicKeyword")) {
      var topics = query.subquery(Long.class);
      var topic = topics.from(SubjectTopic.class);
      String keyword = params.get("topicKeyword").replace("%", "\\%").replace("_", "\\_");
      topics
          .select(topic.get("id"))
          .where(
              cb.equal(topic.get("userId"), root.get("userId")),
              cb.equal(topic.get("studentId"), root.get("studentId")),
              cb.like(topic.get("name"), "%" + keyword + "%", '\\'));
      predicates.add(root.get("topicId").in(topics));
    }
    // 日期按上海时区半开区间，避免遗漏结束日期当晚的错题。

    for (String field : List.of("startDate", "endDate"))
      if (params.containsKey(field)) {
        try {
          Instant date =
              LocalDate.parse(params.get(field))
                  .plusDays(field.equals("endDate") ? 1 : 0)
                  .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                  .toInstant();
          predicates.add(
              field.equals("startDate")
                  ? cb.greaterThanOrEqualTo(
                      root.get(sortField.equals("practicedAt") ? "practicedAt" : "createdAt"), date)
                  : cb.lessThan(
                      root.get(sortField.equals("practicedAt") ? "practicedAt" : "createdAt"),
                      date));
        } catch (Exception e) {
          throw ApiException.badRequest("日期格式为yyyy-MM-dd");
        }
      }
    for (String key : List.of("start", "end"))
      if (params.containsKey(key)) {
        try {
          Instant instant = Instant.parse(params.get(key));
          predicates.add(
              key.equals("start")
                  ? cb.greaterThanOrEqualTo(root.get("createdAt"), instant)
                  : cb.lessThan(root.get("createdAt"), instant));
        } catch (Exception e) {
          throw ApiException.badRequest("start/end格式为UTC ISO8601");
        }
      }
    if (params.containsKey("cursor")) {
      JsonNode cursor = verifyCursor(params.get("cursor"));
      if (!scope.equals(cursor.path("scope").asText())) throw ApiException.badRequest("游标与筛选条件不一致");
      Object boundary = convert(cursor.get("value"), root.get(sortField).getJavaType());
      Object id = convert(cursor.get("id"), root.get("id").getJavaType());
      @SuppressWarnings("unchecked")
      Expression<? extends Comparable> expr =
          (Expression<? extends Comparable>) (Expression<?>) root.get(sortField);
      Predicate after =
          descending
              ? cb.lessThan(expr, (Comparable) boundary)
              : cb.greaterThan(expr, (Comparable) boundary);
      @SuppressWarnings("unchecked")
      Expression<? extends Comparable> idExpr =
          (Expression<? extends Comparable>) (Expression<?>) root.get("id");
      predicates.add(
          cb.or(
              after,
              cb.and(
                  cb.equal(root.get(sortField), boundary),
                  cb.greaterThan(idExpr, (Comparable) id))));
    }
    query.where(predicates.toArray(Predicate[]::new));
    query.orderBy(
        descending ? cb.desc(root.get(sortField)) : cb.asc(root.get(sortField)),
        cb.asc(root.get("id")));
    List<T> rows = em.createQuery(query).setMaxResults(limit + 1).getResultList();
    boolean more = rows.size() > limit;
    if (more) rows = new ArrayList<>(rows.subList(0, limit));
    String next = null;
    if (more) {
      T last = rows.get(rows.size() - 1);
      Object value = property(last, sortField);
      next =
          signCursor(
              map(
                  "scope",
                  scope,
                  "value",
                  value instanceof Instant ? value.toString() : value,
                  "id",
                  property(last, "id"),
                  "expiresAt",
                  Instant.now().plusSeconds(86400).getEpochSecond()));
    }
    return map("items", rows.stream().map(dto).toList(), "nextCursor", next, "hasMore", more);
  }

  private Object property(Object value, String field) {
    try {
      return value
          .getClass()
          .getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1))
          .invoke(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Object convert(JsonNode value, Class<?> type) {
    if (type == Instant.class) return Instant.parse(value.asText());
    if (type == Long.class || type == long.class) return value.asLong();
    if (type == Integer.class || type == int.class) return value.asInt();
    return value.asText();
  }

  private String signCursor(Object value) {
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(encode(value).getBytes(StandardCharsets.UTF_8));
    return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(payload));
  }

  private byte[] mac(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(cursorKey, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode verifyCursor(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 2
          || !MessageDigest.isEqual(mac(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
        throw new IllegalArgumentException();
      JsonNode cursor = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
      if (cursor.path("expiresAt").asLong() < Instant.now().getEpochSecond())
        throw new IllegalArgumentException();
      return cursor;
    } catch (Exception e) {
      throw ApiException.badRequest("游标无效或过期");
    }
  }

  public static void revision(Integer current, JsonNode body) {
    int expected = integer(body, "revision", -1, 0, Integer.MAX_VALUE);
    if (expected != current)
      throw new ApiException(4091, "数据已更新，请刷新", map("current", map("revision", current)));
  }

  public static Map<String, Object> empty() {
    return map();
  }

  public EntityManager entityManager() {
    return em;
  }
}
