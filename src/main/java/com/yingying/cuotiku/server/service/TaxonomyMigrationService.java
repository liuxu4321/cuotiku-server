package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.repository.BookAddIdempotencyRepository;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 历史回填（CONTRACT §2.3 / BACKEND-REVIEW GAP-03）：幂等启动 runner。
 * 仅处理 subject_id IS NULL 的行；六科旧名映射预设科目，未知旧值创建自定义科目保留；
 * 历史题 topic_id 保持 NULL；不触发激活。
 */
@Service
public class TaxonomyMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyMigrationService.class);

    private final BookEntryRepository entryRepository;
    private final UserSubjectRepository subjectRepository;
    private final BookAddIdempotencyRepository idempotencyRepository;
    private final TaxonomyService taxonomyService;
    private final AppProperties properties;
    private final ObjectProvider<TaxonomyMigrationService> self;

    public TaxonomyMigrationService(BookEntryRepository entryRepository,
                                    UserSubjectRepository subjectRepository,
                                    BookAddIdempotencyRepository idempotencyRepository,
                                    TaxonomyService taxonomyService,
                                    AppProperties properties,
                                    ObjectProvider<TaxonomyMigrationService> self) {
        this.entryRepository = entryRepository;
        this.subjectRepository = subjectRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.taxonomyService = taxonomyService;
        this.properties = properties;
        self.getClass();
        this.self = self;
    }

    public record BackfillSummary(long users, long subjectsCreated, long entriesUpdated) {}

    public BackfillSummary backfillAll() {
        List<Long> userIds = entryRepository.findUserIdsWithUnmigratedEntries();
        long entriesUpdated = 0;
        for (Long userId : userIds) {
            taxonomyService.initSubjectsWithRetry(userId);
            for (String rawName : entryRepository.findUnmigratedSubjectNames(userId)) {
                if (rawName == null || rawName.isBlank()) {
                    continue;
                }
                entriesUpdated += self.getObject().migrateName(userId, rawName);
            }
        }
        return new BackfillSummary(userIds.size(), 0, entriesUpdated);
    }

    /**
     * 单个（用户, 旧科目名）独立事务：避免外层事务快照早于初始化提交（MySQL REPEATABLE READ）
     * 导致误走自定义分支撞唯一键。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int migrateName(Long userId, String rawName) {
        UserSubject subject;
        try {
            subject = resolveOrCreate(userId, rawName);
        } catch (DataIntegrityViolationException e) {
            log.debug("[迁移] 并发创建科目冲突，跳过本次（下次启动重试） userId={} name={}", userId, rawName);
            return 0;
        }
        if (subject == null) {
            return 0;
        }
        int updated = entryRepository.backfillSubjectId(userId, rawName, subject.getId());
        log.debug("[迁移] userId={} 旧科目={} → subjectId={} 回填={}题",
                userId, rawName, subject.getId(), updated);
        return updated;
    }

    private UserSubject resolveOrCreate(Long userId, String rawName) {
        String normalized = NameNormalizer.normalized(rawName);
        for (TaxonomyService.Template template : TaxonomyService.PRESETS) {
            if (NameNormalizer.normalized(template.name()).equals(normalized)) {
                Optional<UserSubject> preset =
                        subjectRepository.findByUserIdAndSystemKey(userId, template.systemKey());
                if (preset.isPresent()) {
                    return preset.get();
                }
            }
        }
        Optional<UserSubject> custom = subjectRepository.findByUserIdAndNormalizedName(userId, normalized);
        if (custom.isPresent()) {
            return custom.get();
        }
        UserSubject subject = new UserSubject();
        subject.setUserId(userId);
        subject.setName(NameNormalizer.display(rawName));
        subject.setNormalizedName(normalized);
        subject.setSystemKey(null);
        subject.setSortOrder(subjectRepository.maxSortOrder(userId) + 10);
        return subjectRepository.saveAndFlush(subject);
    }

    /** 幂等记录保留 ≥7 天（CONTRACT §7）。 */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void purgeExpiredIdempotency() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        long deleted = idempotencyRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("[幂等] 清理过期记录 {} 条", deleted);
        }
    }
}
