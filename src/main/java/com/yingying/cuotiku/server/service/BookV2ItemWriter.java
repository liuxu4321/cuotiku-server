package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.dto.BookV2Dto.BatchAddItem;
import com.yingying.cuotiku.server.dto.BookV2Dto.ItemResultDto;
import com.yingying.cuotiku.server.entity.*;
import com.yingying.cuotiku.server.repository.*;
import com.yingying.cuotiku.server.storage.BookStorage;
import com.yingying.cuotiku.server.web.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 逐项独立事务写入（CONTRACT §5.3）：仅成功项落库，失败不影响同批其他项。
 */
@Component
public class BookV2ItemWriter {

    private static final java.util.Set<String> ERROR_TYPES =
            java.util.Set.of("马虎", "不会", "概念不清", "其他");

    private final BookEntryRepository entryRepository;
    private final UserSubjectRepository subjectRepository;
    private final SubjectTopicRepository topicRepository;
    private final BookAddIdempotencyRepository idempotencyRepository;
    private final BookService bookService;
    private final BookStorage storage;
    private final TaxonomyService taxonomyService;

    public BookV2ItemWriter(BookEntryRepository entryRepository,
                            UserSubjectRepository subjectRepository,
                            SubjectTopicRepository topicRepository,
                            BookAddIdempotencyRepository idempotencyRepository,
                            BookService bookService,
                            BookStorage storage,
                            TaxonomyService taxonomyService) {
        this.entryRepository = entryRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.bookService = bookService;
        this.storage = storage;
        this.taxonomyService = taxonomyService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ItemResultDto writeItem(User user, String requestId, BatchAddItem item) {
        Optional<BookAddIdempotency> hit = idempotencyRepository
                .findByUserIdAndRequestIdAndClientId(user.getId(), requestId, item.clientId());
        if (hit.isPresent()) {
            return new ItemResultDto(item.clientId(), "DUPLICATE", hit.get().getEntryId(), null, null);
        }
        try {
            UserSubject subject = subjectRepository.findByUserIdAndId(user.getId(), item.subjectId())
                    .orElseThrow(() -> ApiException.notFound("分类不存在"));
            if (!UserSubject.STATUS_ACTIVE.equals(subject.getStatus())) {
                throw ApiException.badRequest("科目已停用，不能用于新归类");
            }
            SubjectTopic topic = null;
            if (item.topicId() != null) {
                topic = topicRepository.findByUserIdAndId(user.getId(), item.topicId())
                        .orElseThrow(() -> ApiException.notFound("分类不存在"));
                if (!topic.getSubjectId().equals(subject.getId())) {
                    throw ApiException.badRequest("主题不属于该科目");
                }
                if (!UserSubject.STATUS_ACTIVE.equals(topic.getStatus())) {
                    throw ApiException.badRequest("主题已停用，不能用于新归类");
                }
            }
            String errorType = item.errorType() == null || item.errorType().isBlank()
                    ? "其他" : item.errorType().trim();
            if (!ERROR_TYPES.contains(errorType)) {
                throw ApiException.badRequest("错误类型只能是：" + String.join("、", ERROR_TYPES));
            }
            BookService.PreparedImage image = bookService.prepareImage(item.imageBase64());

            String id = UUID.randomUUID().toString();
            Instant now = Instant.now();
            String key = "book/%d/%s/%s.zip".formatted(user.getId(),
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMM")
                            .withZone(java.time.ZoneId.systemDefault()).format(now), id);
            storage.put(key, image.zip());

            BookEntry entry = new BookEntry();
            entry.setId(id);
            entry.setUserId(user.getId());
            entry.setGrade(item.grade());
            entry.setTerm(item.term());
            entry.setSubject(subject.getName());
            entry.setSubjectId(subject.getId());
            entry.setTopicId(topic == null ? null : topic.getId());
            entry.setErrorType(errorType);
            entry.setRemark(item.remark() == null || item.remark().isBlank() ? null : item.remark().trim());
            entry.setAnswer(item.answer() == null || item.answer().isBlank() ? null : item.answer().trim());
            entry.setCreatedAt(now);
            entry.setWidth(image.width());
            entry.setHeight(image.height());
            entry.setPracticeCount(0);
            entry.setObjectKey(key);
            entry.setFormat(image.format());
            entry.setHasThumb(image.thumb() != null);
            entry.setSizeBytes(image.bytes().length);
            entryRepository.save(entry);

            BookAddIdempotency idempotency = new BookAddIdempotency();
            idempotency.setUserId(user.getId());
            idempotency.setRequestId(requestId);
            idempotency.setClientId(item.clientId());
            idempotency.setEntryId(id);
            idempotencyRepository.save(idempotency);

            taxonomyService.activateIfNotYet(user);
            return new ItemResultDto(item.clientId(), "SUCCESS", id, null, null);
        } catch (ApiException e) {
            return new ItemResultDto(item.clientId(), "FAILED", null, e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            return new ItemResultDto(item.clientId(), "FAILED", null, 500, "写入失败：" + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyResultDto reclassifyItem(
            User user, com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyItem item) {
        try {
            BookEntry entry = entryRepository.findByIdAndUserId(item.entryId(), user.getId())
                    .orElseThrow(() -> ApiException.notFound("错题不存在"));
            UserSubject subject = subjectRepository.findByUserIdAndId(user.getId(), item.subjectId())
                    .orElseThrow(() -> ApiException.notFound("分类不存在"));
            if (!UserSubject.STATUS_ACTIVE.equals(subject.getStatus())) {
                throw ApiException.badRequest("科目已停用，不能用于新归类");
            }
            if (item.topicId() != null) {
                SubjectTopic topic = topicRepository.findByUserIdAndId(user.getId(), item.topicId())
                        .orElseThrow(() -> ApiException.notFound("分类不存在"));
                if (!topic.getSubjectId().equals(subject.getId())) {
                    throw ApiException.badRequest("主题不属于该科目");
                }
                if (!UserSubject.STATUS_ACTIVE.equals(topic.getStatus())) {
                    throw ApiException.badRequest("主题已停用，不能用于新归类");
                }
            }
            entry.setSubjectId(subject.getId());
            entry.setTopicId(item.topicId());
            entry.setSubject(subject.getName());
            entryRepository.save(entry);
            taxonomyService.activateIfNotYet(user);
            return new com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyResultDto(
                    item.entryId(), "SUCCESS", 0, null);
        } catch (ApiException e) {
            return new com.yingying.cuotiku.server.dto.BookV2Dto.ReclassifyResultDto(
                    item.entryId(), "FAILED", e.getCode(), e.getMessage());
        }
    }
}
