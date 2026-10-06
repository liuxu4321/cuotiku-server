package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.TaxonomyDto.*;
import com.yingying.cuotiku.server.entity.BookEntry;
import com.yingying.cuotiku.server.entity.SubjectTopic;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.repository.SubjectTopicRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SubjectTopicService {

    private static final Logger log = LoggerFactory.getLogger(SubjectTopicService.class);
    private static final int MAX_SUBJECTS = 50;
    private static final int MAX_TOPICS = 200;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final UserSubjectRepository subjectRepository;
    private final SubjectTopicRepository topicRepository;
    private final BookEntryRepository entryRepository;
    private final TaxonomyService taxonomyService;

    public SubjectTopicService(UserSubjectRepository subjectRepository,
                               SubjectTopicRepository topicRepository,
                               BookEntryRepository entryRepository,
                               TaxonomyService taxonomyService) {
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.entryRepository = entryRepository;
        this.taxonomyService = taxonomyService;
    }

    /* ---------------- 科目 ---------------- */

    @Transactional
    public List<SubjectDto> listSubjects(User user) {
        taxonomyService.initSubjectsWithRetry(user.getId());
        List<UserSubject> subjects = subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId());
        Map<Long, Long> topicCounts = group(topicRepository.countGroupedBySubject(user.getId()));
        Map<Long, Long> entryCounts = group(entryRepository.countGroupedBySubjectId(user.getId()));
        return subjects.stream()
                .map(s -> toDto(s, topicCounts.getOrDefault(s.getId(), 0L),
                        entryCounts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    @Transactional
    public SubjectDto createSubject(User user, NameRequest request) {
        ensureWriteAllowed(user);
        if (subjectRepository.countByUserId(user.getId()) >= MAX_SUBJECTS) {
            throw ApiException.badRequest("科目数量已达上限（" + MAX_SUBJECTS + "），请停用或删除不用的科目");
        }
        String name = NameNormalizer.validate(request.name(), "科目");
        String normalized = NameNormalizer.normalized(name);
        if (subjectRepository.findByUserIdAndNormalizedName(user.getId(), normalized).isPresent()) {
            throw new ApiException(4093, "科目名称已存在");
        }
        UserSubject subject = new UserSubject();
        subject.setUserId(user.getId());
        subject.setName(name);
        subject.setNormalizedName(normalized);
        subject.setSortOrder(subjectRepository.maxSortOrder(user.getId()) + 10);
        subjectRepository.save(subject);
        taxonomyService.activateIfNotYet(user);
        log.debug("[分类] 创建科目 userId={} id={} name={}", user.getId(), subject.getId(), name);
        return toDto(subject, 0, 0);
    }

    @Transactional
    public SubjectDto updateSubject(User user, Long id, UpdateSubjectRequest request) {
        ensureWriteAllowed(user);
        UserSubject subject = requireSubject(user, id);
        checkRevision(subject.getRevision(), request.revision(), toDto(subject, counts(subject)));
        if (request.name() != null && !request.name().isBlank()) {
            String name = NameNormalizer.validate(request.name(), "科目");
            String normalized = NameNormalizer.normalized(name);
            if (!normalized.equals(subject.getNormalizedName())
                    && subjectRepository.findByUserIdAndNormalizedName(user.getId(), normalized).isPresent()) {
                throw new ApiException(4093, "科目名称已存在");
            }
            subject.setName(name);
            subject.setNormalizedName(normalized);
        }
        if (request.status() != null && !request.status().isBlank()) {
            subject.setStatus(parseStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            subject.setSortOrder(request.sortOrder());
        }
        subject.setRevision(subject.getRevision() + 1);
        subjectRepository.save(subject);
        taxonomyService.activateIfNotYet(user);
        return toDto(subject, counts(subject));
    }

    @Transactional
    public List<SubjectDto> reorderSubjects(User user, ReorderRequest request) {
        ensureWriteAllowed(user);
        List<UserSubject> all = subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId());
        Map<Long, UserSubject> byId = new HashMap<>();
        for (UserSubject s : all) {
            byId.put(s.getId(), s);
        }
        Set<Long> requested = new HashSet<>();
        for (ReorderItem item : request.items()) {
            requested.add(item.id());
        }
        if (requested.size() != request.items().size() || requested.size() != all.size()
                || !requested.equals(byId.keySet())) {
            throw ApiException.badRequest("排序列表必须恰好覆盖全部科目");
        }
        List<SubjectDto> latest = new ArrayList<>();
        int order = 10;
        for (ReorderItem item : request.items()) {
            UserSubject subject = byId.get(item.id());
            if (subject.getRevision() != item.revision()) {
                for (UserSubject s : subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId())) {
                    latest.add(toDto(s, counts(s)));
                }
                throw new ApiException(4091, "分类已被其他设备修改，请刷新后重试", latest);
            }
            subject.setSortOrder(order);
            order += 10;
            subject.setRevision(subject.getRevision() + 1);
            subjectRepository.save(subject);
        }
        taxonomyService.activateIfNotYet(user);
        return listSubjects(user);
    }

    @Transactional
    public void deleteSubject(User user, Long id) {
        ensureWriteAllowed(user);
        UserSubject subject = requireSubject(user, id);
        if (topicRepository.existsBySubjectId(subject.getId())
                || entryRepository.existsBySubjectId(subject.getId())) {
            throw new ApiException(4092, "存在关联数据，请改用停用");
        }
        subjectRepository.delete(subject);
        taxonomyService.activateIfNotYet(user);
        log.debug("[分类] 删除科目 userId={} id={}", user.getId(), id);
    }

    /* ---------------- 主题 ---------------- */

    @Transactional(readOnly = true)
    public List<TopicDto> listTopics(User user, Long subjectId) {
        requireSubject(user, subjectId);
        List<SubjectTopic> topics = topicRepository.findBySubjectIdOrderBySortOrderAscIdAsc(subjectId);
        Map<Long, Long> entryCounts = group(entryRepository.countGroupedByTopicId(user.getId()));
        return topics.stream().map(t -> toDto(t, entryCounts.getOrDefault(t.getId(), 0L))).toList();
    }

    @Transactional
    public TopicDto createTopic(User user, Long subjectId, NameRequest request) {
        ensureWriteAllowed(user);
        UserSubject subject = requireSubject(user, subjectId);
        if (topicRepository.countBySubjectId(subjectId) >= MAX_TOPICS) {
            throw ApiException.badRequest("主题数量已达上限（" + MAX_TOPICS + "），请停用或删除不用的主题");
        }
        String name = NameNormalizer.validate(request.name(), "主题");
        String normalized = NameNormalizer.normalized(name);
        if (topicRepository.findBySubjectIdAndNormalizedName(subjectId, normalized).isPresent()) {
            throw new ApiException(4093, "该科目下主题名称已存在");
        }
        SubjectTopic topic = new SubjectTopic();
        topic.setUserId(user.getId());
        topic.setSubjectId(subject.getId());
        topic.setName(name);
        topic.setNormalizedName(normalized);
        topic.setSortOrder(topicRepository.maxSortOrder(subjectId) + 10);
        topicRepository.save(topic);
        taxonomyService.activateIfNotYet(user);
        log.debug("[分类] 创建主题 userId={} subjectId={} id={} name={}",
                user.getId(), subjectId, topic.getId(), name);
        return toDto(topic, 0);
    }

    @Transactional
    public TopicDto updateTopic(User user, Long id, UpdateTopicRequest request) {
        ensureWriteAllowed(user);
        SubjectTopic topic = requireTopic(user, id);
        checkRevision(topic.getRevision(), request.revision(), toDto(topic, entryCount(topic.getId())));
        if (request.name() != null && !request.name().isBlank()) {
            String name = NameNormalizer.validate(request.name(), "主题");
            String normalized = NameNormalizer.normalized(name);
            if (!normalized.equals(topic.getNormalizedName())
                    && topicRepository.findBySubjectIdAndNormalizedName(topic.getSubjectId(), normalized).isPresent()) {
                throw new ApiException(4093, "该科目下主题名称已存在");
            }
            topic.setName(name);
            topic.setNormalizedName(normalized);
        }
        if (request.status() != null && !request.status().isBlank()) {
            topic.setStatus(parseStatus(request.status()));
        }
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        topic.setRevision(topic.getRevision() + 1);
        topicRepository.save(topic);
        taxonomyService.activateIfNotYet(user);
        return toDto(topic, entryCount(topic.getId()));
    }

    @Transactional
    public List<TopicDto> reorderTopics(User user, Long subjectId, ReorderRequest request) {
        ensureWriteAllowed(user);
        requireSubject(user, subjectId);
        List<SubjectTopic> all = topicRepository.findBySubjectIdOrderBySortOrderAscIdAsc(subjectId);
        Map<Long, SubjectTopic> byId = new HashMap<>();
        for (SubjectTopic t : all) {
            byId.put(t.getId(), t);
        }
        Set<Long> requested = new HashSet<>();
        for (ReorderItem item : request.items()) {
            requested.add(item.id());
        }
        if (requested.size() != request.items().size() || requested.size() != all.size()
                || !requested.equals(byId.keySet())) {
            throw ApiException.badRequest("排序列表必须恰好覆盖该科目全部主题");
        }
        int order = 10;
        for (ReorderItem item : request.items()) {
            SubjectTopic topic = byId.get(item.id());
            if (topic.getRevision() != item.revision()) {
                throw new ApiException(4091, "分类已被其他设备修改，请刷新后重试",
                        listTopics(user, subjectId));
            }
            topic.setSortOrder(order);
            order += 10;
            topic.setRevision(topic.getRevision() + 1);
            topicRepository.save(topic);
        }
        taxonomyService.activateIfNotYet(user);
        return listTopics(user, subjectId);
    }

    @Transactional
    public void deleteTopic(User user, Long id) {
        ensureWriteAllowed(user);
        SubjectTopic topic = requireTopic(user, id);
        if (entryRepository.existsByTopicId(topic.getId())) {
            throw new ApiException(4092, "存在关联数据，请改用停用");
        }
        topicRepository.delete(topic);
        taxonomyService.activateIfNotYet(user);
        log.debug("[分类] 删除主题 userId={} id={}", user.getId(), id);
    }

    /* ---------------- 共享 ---------------- */

    public void ensureWriteAllowed(User user) {
        if (!taxonomyService.entryOpen() && !taxonomyService.activated(user)) {
            throw ApiException.forbidden("分类功能未开放");
        }
    }

    public UserSubject requireSubject(User user, Long id) {
        return subjectRepository.findByUserIdAndId(user.getId(), id)
                .orElseThrow(() -> ApiException.notFound("分类不存在"));
    }

    public SubjectTopic requireTopic(User user, Long id) {
        return topicRepository.findByUserIdAndId(user.getId(), id)
                .orElseThrow(() -> ApiException.notFound("分类不存在"));
    }

    public SubjectTopic requireTopicUnderSubject(User user, Long subjectId, Long topicId) {
        SubjectTopic topic = requireTopic(user, topicId);
        if (!topic.getSubjectId().equals(subjectId)) {
            throw ApiException.badRequest("主题不属于该科目");
        }
        return topic;
    }

    private void checkRevision(int current, Integer requested, Object latest) {
        if (requested == null || requested != current) {
            throw new ApiException(4091, "分类已被其他设备修改，请刷新后重试", latest);
        }
    }

    private String parseStatus(String status) {
        if (UserSubject.STATUS_ACTIVE.equalsIgnoreCase(status)) {
            return UserSubject.STATUS_ACTIVE;
        }
        if (UserSubject.STATUS_DISABLED.equalsIgnoreCase(status)) {
            return UserSubject.STATUS_DISABLED;
        }
        throw ApiException.badRequest("状态只能是 ACTIVE 或 DISABLED");
    }

    private long[] counts(UserSubject subject) {
        return new long[]{topicRepository.countBySubjectId(subject.getId()),
                entryRepository.countBySubjectId(subject.getId())};
    }

    private long entryCount(Long topicId) {
        return entryRepository.countByTopicId(topicId);
    }

    private Map<Long, Long> group(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private SubjectDto toDto(UserSubject s, long topicCount, long entryCount) {
        return new SubjectDto(s.getId(), s.getName(), s.getSystemKey(), s.getSortOrder(), s.getStatus(),
                s.getRevision(), topicCount, entryCount, TIME_FORMAT.format(s.getUpdatedAt()));
    }

    private SubjectDto toDto(UserSubject s, long[] counts) {
        return toDto(s, counts[0], counts[1]);
    }

    private TopicDto toDto(SubjectTopic t, long entryCount) {
        return new TopicDto(t.getId(), t.getSubjectId(), t.getName(), t.getSortOrder(), t.getStatus(),
                t.getRevision(), entryCount, TIME_FORMAT.format(t.getUpdatedAt()));
    }
}
