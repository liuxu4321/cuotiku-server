package com.yingying.cuotiku.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingying.cuotiku.server.dto.BookV2Dto.BatchAddItem;
import com.yingying.cuotiku.server.dto.BookV2Dto.BatchAddRequest;
import com.yingying.cuotiku.server.dto.BookV2Dto.BatchAddResponse;
import com.yingying.cuotiku.server.dto.BookV2Dto.EntryDtoV2;
import com.yingying.cuotiku.server.dto.BookV2Dto.ItemResultDto;
import com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyItem;
import com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyRequest;
import com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyResponse;
import com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyResultDto;
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
import java.util.List;
import java.util.Map;

@Service
public class BookV2Service {

    private static final Logger log = LoggerFactory.getLogger(BookV2Service.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final List<String> ERROR_TYPES = List.of("马虎", "不会", "概念不清", "其他");

    private final BookV2ItemWriter itemWriter;
    private final SubjectTopicService subjectTopicService;
    private final BookEntryRepository entryRepository;
    private final UserSubjectRepository subjectRepository;
    private final SubjectTopicRepository topicRepository;
    private final BookService bookService;
    private final TaxonomyService taxonomyService;

    public BookV2Service(BookV2ItemWriter itemWriter,
                         SubjectTopicService subjectTopicService,
                         BookEntryRepository entryRepository,
                         UserSubjectRepository subjectRepository,
                         SubjectTopicRepository topicRepository,
                         BookService bookService,
                         TaxonomyService taxonomyService) {
        this.itemWriter = itemWriter;
        this.subjectTopicService = subjectTopicService;
        this.entryRepository = entryRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.bookService = bookService;
        this.taxonomyService = taxonomyService;
    }

    public BatchAddResponse batchAdd(User user, BatchAddRequest request) {
        subjectTopicService.ensureWriteAllowed(user);
        List<ItemResultDto> results = new ArrayList<>();
        for (BatchAddItem item : request.items()) {
            results.add(itemWriter.writeItem(user, request.requestId(), item));
        }
        long success = results.stream().filter(r -> !"FAILED".equals(r.status())).count();
        log.debug("[错题v2] 批量加入 userId={} requestId={} 项={} 成功={}",
                user.getId(), request.requestId(), request.items().size(), success);
        return new BatchAddResponse(results);
    }

    public ReclassifyResponse reclassify(User user, ReclassifyRequest request) {
        subjectTopicService.ensureWriteAllowed(user);
        List<ReclassifyResultDto> results = new ArrayList<>();
        for (ReclassifyItem item : request.items()) {
            results.add(itemWriter.reclassifyItem(user, item));
        }
        return new ReclassifyResponse(results);
    }

    @Transactional
    public EntryDtoV2 edit(User user, String entryId, JsonNode body) {
        subjectTopicService.ensureWriteAllowed(user);
        BookEntry entry = entryRepository.findByIdAndUserId(entryId, user.getId())
                .orElseThrow(() -> ApiException.notFound("错题不存在"));
        boolean classificationChanged = false;

        if (body.hasNonNull("grade")) {
            int grade = body.path("grade").asInt();
            if (grade < 1 || grade > 12) {
                throw ApiException.badRequest("年级取值1-12");
            }
            entry.setGrade(grade);
        }
        if (body.hasNonNull("term")) {
            int term = body.path("term").asInt();
            if (term < 1 || term > 2) {
                throw ApiException.badRequest("学期取值1或2");
            }
            entry.setTerm(term);
        }
        if (body.has("subjectId") && !body.path("subjectId").isNull()) {
            Long targetSubjectId = body.path("subjectId").asLong();
            if (!body.has("topicId")) {
                throw ApiException.badRequest("更换科目时必须显式指定主题或清空");
            }
            UserSubject target = subjectTopicService.requireSubject(user, targetSubjectId);
            Long topicId = resolveTopic(user, target, body.path("topicId"), true);
            entry.setSubjectId(target.getId());
            entry.setTopicId(topicId);
            entry.setSubject(target.getName());
            classificationChanged = true;
        } else if (body.has("topicId")) {
            if (entry.getSubjectId() == null) {
                throw ApiException.badRequest("该题尚无科目，请同时指定 subjectId");
            }
            UserSubject current = subjectTopicService.requireSubject(user, entry.getSubjectId());
            entry.setTopicId(resolveTopic(user, current, body.path("topicId"), true));
            classificationChanged = true;
        }
        if (body.hasNonNull("errorType")) {
            String errorType = body.path("errorType").asText().trim();
            if (!ERROR_TYPES.contains(errorType)) {
                throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
            }
            entry.setErrorType(errorType);
        }
        if (body.has("remark")) {
            entry.setRemark(blankToNull(body.path("remark").asText(null)));
        }
        if (body.has("answer")) {
            entry.setAnswer(blankToNull(body.path("answer").asText(null)));
        }
        entryRepository.save(entry);
        if (classificationChanged) {
            taxonomyService.activateIfNotYet(user);
        }
        log.debug("[错题v2] 编辑 id={} 分类变更={}", entryId, classificationChanged);
        return toV2Dto(entry);
    }

    private Long resolveTopic(User user, UserSubject subject, JsonNode topicNode, boolean forWrite) {
        if (topicNode == null || topicNode.isNull()) {
            return null;
        }
        Long topicId = topicNode.asLong();
        SubjectTopic topic = subjectTopicService.requireTopic(user, topicId);
        if (!topic.getSubjectId().equals(subject.getId())) {
            throw ApiException.badRequest("主题不属于该科目");
        }
        if (forWrite && !UserSubject.STATUS_ACTIVE.equals(topic.getStatus())) {
            throw ApiException.badRequest("主题已停用，不能用于新归类");
        }
        if (forWrite && !UserSubject.STATUS_ACTIVE.equals(subject.getStatus())) {
            throw ApiException.badRequest("科目已停用，不能用于新归类");
        }
        return topic.getId();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public EntryDtoV2 toV2Dto(BookEntry entry) {
        UserSubject subject = entry.getSubjectId() == null ? null
                : subjectRepository.findById(entry.getSubjectId()).orElse(null);
        SubjectTopic topic = entry.getTopicId() == null ? null
                : topicRepository.findById(entry.getTopicId()).orElse(null);
        Map<String, com.yingying.cuotiku.server.repository.BookPracticeRecordRepository.EntryStat> stats =
                bookService.statMap(List.of(entry.getId()));
        var stat = stats.get(entry.getId());
        long total = stat == null ? 0 : stat.getTotal();
        long correct = stat == null ? 0 : stat.getCorrect();
        return new EntryDtoV2(
                entry.getId(),
                entry.getGrade(),
                entry.getTerm(),
                entry.getSubject(),
                entry.getErrorType(),
                entry.getCreatedAt().toEpochMilli(),
                TIME_FORMAT.format(entry.getCreatedAt()),
                entry.getWidth(),
                entry.getHeight(),
                entry.getPracticeCount(),
                entry.getRemark(),
                entry.getAnswer(),
                "/api/book/entries/" + entry.getId() + "/image",
                "/api/book/entries/" + entry.getId() + "/image?kind=thumb",
                total,
                correct,
                total == 0 ? null : Math.round(correct * 1000.0 / total) / 10.0,
                stat == null || stat.getLastAt() == null ? null : stat.getLastAt().toEpochMilli(),
                entry.getSubjectId(),
                subject == null ? null : subject.getName(),
                entry.getTopicId(),
                topic == null ? null : topic.getName(),
                subject == null ? null : subject.getStatus(),
                topic == null ? null : topic.getStatus());
    }
}
