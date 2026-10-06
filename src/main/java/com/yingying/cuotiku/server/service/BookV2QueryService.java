package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.AdminUserDto.PageResponse;
import com.yingying.cuotiku.server.dto.BookDto.TypeStat;
import com.yingying.cuotiku.server.dto.BookV2Dto.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookV2QueryService {

    private static final Logger log = LoggerFactory.getLogger(BookV2QueryService.class);
    private static final List<String> ERROR_TYPE_ORDER = List.of("马虎", "不会", "概念不清", "其他");
    private static final Set<String> ERROR_TYPES = Set.of("马虎", "不会", "概念不清", "其他");

    private final BookEntryRepository entryRepository;
    private final UserSubjectRepository subjectRepository;
    private final SubjectTopicRepository topicRepository;
    private static final java.time.format.DateTimeFormatter TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault());

    private final AbilityService abilityService;
    private final BookService bookService;

    public BookV2QueryService(BookEntryRepository entryRepository,
                              UserSubjectRepository subjectRepository,
                              SubjectTopicRepository topicRepository,
                              AbilityService abilityService,
                              BookService bookService) {
        this.entryRepository = entryRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.abilityService = abilityService;
        this.bookService = bookService;
    }

    public record Scope(Integer grade, Integer term, Long subjectId, Long topicId,
                        Boolean unclassified, String errorType, Instant start, Instant end) {

        public int topicMode() {
            if (topicId != null) {
                return 1;
            }
            return Boolean.TRUE.equals(unclassified) ? 2 : 0;
        }
    }

    public static Scope parseScope(Integer grade, Integer term, Long subjectId, Long topicId,
                                   Boolean unclassified, String errorType, Instant start, Instant end) {
        if (errorType != null && !errorType.isBlank() && !ERROR_TYPES.contains(errorType)) {
            throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
        }
        return new Scope(grade, term, subjectId, topicId, unclassified,
                errorType == null || errorType.isBlank() ? null : errorType, start, end);
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryDtoV2> list(User user, Scope scope, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<BookEntry> result = entryRepository.searchV2(user.getId(), scope.grade(), scope.term(),
                scope.subjectId(), scope.errorType(), scope.topicMode(), scope.topicId(),
                scope.start(), scope.end(), pageable);
        List<EntryDtoV2> items = toV2Dtos(result.getContent());
        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public RandomV2Response random(User user, RandomV2Request request) {
        Scope base = parseScope(request.grade(), request.term(), request.subjectId(),
                request.topicId(), request.unclassified(), null, null, null);
        int totalRequested = 0;
        for (Map.Entry<String, Integer> e : request.counts().entrySet()) {
            if (!ERROR_TYPES.contains(e.getKey())) {
                throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
            }
            if (e.getValue() == null || e.getValue() < 0 || e.getValue() > 100) {
                throw ApiException.badRequest("单类型抽取数量须在0-100之间");
            }
            totalRequested += e.getValue();
        }
        if (totalRequested == 0) {
            throw ApiException.badRequest("抽取总数量需大于0");
        }
        if (totalRequested > 200) {
            throw ApiException.badRequest("单次最多抽取200道");
        }
        List<EntryDtoV2> items = new ArrayList<>();
        Map<String, TypeStat> byType = new LinkedHashMap<>();
        int selected = 0;
        for (String type : ERROR_TYPE_ORDER) {
            int need = request.counts().getOrDefault(type, 0);
            List<BookEntry> pool = entryRepository.listV2(user.getId(), base.grade(), base.term(),
                    base.subjectId(), type, base.topicMode(), base.topicId(), null, null);
            List<BookEntry> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            shuffled.sort(Comparator.comparingInt(BookEntry::getPracticeCount));
            List<BookEntry> picked = shuffled.subList(0, Math.min(need, shuffled.size()));
            items.addAll(toV2Dtos(picked));
            selected += picked.size();
            byType.put(type, new TypeStat(need, picked.size(), pool.size()));
        }
        log.debug("[组卷v2] userId={} scope={} 请求={} 抽中={}", user.getId(), base, totalRequested, selected);
        return new RandomV2Response(items, totalRequested, selected, byType);
    }

    @Transactional(readOnly = true)
    public AbilityV2Response ability(User user, Scope scope) {
        List<BookEntry> scoped = entryRepository.listV2(user.getId(), scope.grade(), scope.term(),
                scope.subjectId(), scope.errorType(), scope.topicMode(), scope.topicId(),
                scope.start(), scope.end());
        AbilityService.AbilityCore overallCore = abilityService.computeCore(scoped);
        AbilityModelV2 overall = new AbilityModelV2(null, null, null,
                overallCore.sampleSize(), overallCore.overall(), overallCore.dimensions());

        List<UserSubject> subjects;
        if (scope.subjectId() != null) {
            subjects = List.of(subjectRepository.findByUserIdAndId(user.getId(), scope.subjectId())
                    .orElseThrow(() -> ApiException.notFound("分类不存在")));
        } else {
            subjects = subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(user.getId());
        }
        Map<Long, List<BookEntry>> bySubject = new HashMap<>();
        for (BookEntry entry : scoped) {
            bySubject.computeIfAbsent(entry.getSubjectId() == null ? -1L : entry.getSubjectId(),
                    k -> new ArrayList<>()).add(entry);
        }
        List<AbilityModelV2> models = new ArrayList<>();
        for (UserSubject subject : subjects) {
            List<BookEntry> part = bySubject.getOrDefault(subject.getId(), List.of());
            AbilityService.AbilityCore core = abilityService.computeCore(part);
            models.add(new AbilityModelV2(subject.getId(), subject.getName(), subject.getStatus(),
                    core.sampleSize(), core.overall(), core.dimensions()));
        }
        return new AbilityV2Response(overall, models);
    }

    private List<EntryDtoV2> toV2Dtos(List<BookEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Set<Long> subjectIds = new HashSet<>();
        Set<Long> topicIds = new HashSet<>();
        for (BookEntry entry : entries) {
            if (entry.getSubjectId() != null) {
                subjectIds.add(entry.getSubjectId());
            }
            if (entry.getTopicId() != null) {
                topicIds.add(entry.getTopicId());
            }
        }
        Map<Long, UserSubject> subjectMap = new HashMap<>();
        for (UserSubject s : subjectRepository.findAllById(subjectIds)) {
            subjectMap.put(s.getId(), s);
        }
        Map<Long, SubjectTopic> topicMap = new HashMap<>();
        for (SubjectTopic t : topicRepository.findAllById(topicIds)) {
            topicMap.put(t.getId(), t);
        }
        Map<String, com.yingying.cuotiku.server.repository.BookPracticeRecordRepository.EntryStat> stats =
                bookService.statMap(entries.stream().map(BookEntry::getId).toList());
        List<EntryDtoV2> result = new ArrayList<>();
        for (BookEntry entry : entries) {
            UserSubject subject = entry.getSubjectId() == null ? null : subjectMap.get(entry.getSubjectId());
            SubjectTopic topic = entry.getTopicId() == null ? null : topicMap.get(entry.getTopicId());
            var stat = stats.get(entry.getId());
            long total = stat == null ? 0 : stat.getTotal();
            long correct = stat == null ? 0 : stat.getCorrect();
            result.add(new EntryDtoV2(
                    entry.getId(), entry.getGrade(), entry.getTerm(), entry.getSubject(), entry.getErrorType(),
                    entry.getCreatedAt().toEpochMilli(), TIME_FORMAT.format(entry.getCreatedAt()),
                    entry.getWidth(), entry.getHeight(), entry.getPracticeCount(),
                    entry.getRemark(), entry.getAnswer(),
                    "/api/book/entries/" + entry.getId() + "/image",
                    "/api/book/entries/" + entry.getId() + "/image?kind=thumb",
                    total, correct,
                    total == 0 ? null : Math.round(correct * 1000.0 / total) / 10.0,
                    stat == null || stat.getLastAt() == null ? null : stat.getLastAt().toEpochMilli(),
                    entry.getSubjectId(), subject == null ? null : subject.getName(),
                    entry.getTopicId(), topic == null ? null : topic.getName(),
                    subject == null ? null : subject.getStatus(),
                    topic == null ? null : topic.getStatus()));
        }
        return result;
    }
}
