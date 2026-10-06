package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.repository.BookAddIdempotencyRepository;
import com.yingying.cuotiku.server.repository.BookEntryRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

    public TaxonomyMigrationService(BookEntryRepository entryRepository,
                                    UserSubjectRepository subjectRepository,
                                    BookAddIdempotencyRepository idempotencyRepository,
                                    TaxonomyService taxonomyService,
                                    AppProperties properties) {
        this.entryRepository = entryRepository;
        this.subjectRepository = subjectRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.taxonomyService = taxonomyService;
        this.properties = properties;
    }

    @Bean
    public ApplicationRunner taxonomyBackfillRunner() {
        return args -> {
            if (properties.taxonomy() != null && !properties.taxonomy().backfillOnStartup()) {
                log.info("[迁移] backfill-on-startup=false，跳过启动回填");
                return;
            }
            BackfillSummary summary = backfillAll();
            if (summary.users() > 0) {
                log.info("[迁移] 历史回填完成 用户={} 科目新建={} 题目回填={}",
                        summary.users(), summary.subjectsCreated(), summary.entriesUpdated());
            }
        };
    }

    public record BackfillSummary(long users, long subjectsCreated, long entriesUpdated) {}

    @Transactional
    public BackfillSummary backfillAll() {
        List<Long> userIds = entryRepository.findUserIdsWithUnmigratedEntries();
        long subjectsCreated = 0;
        long entriesUpdated = 0;
        for (Long userId : userIds) {
            taxonomyService.initSubjects(userId);
            for (String rawName : entryRepository.findUnmigratedSubjectNames(userId)) {
                if (rawName == null || rawName.isBlank()) {
                    continue;
                }
                UserSubject subject = resolveOrCreate(userId, rawName);
                if (subject == null) {
                    continue;
                }
                int updated = entryRepository.backfillSubjectId(userId, rawName, subject.getId());
                entriesUpdated += updated;
                log.debug("[迁移] userId={} 旧科目={} → subjectId={} 回填={}题",
                        userId, rawName, subject.getId(), updated);
            }
        }
        return new BackfillSummary(userIds.size(), subjectsCreated, entriesUpdated);
    }

    private UserSubject resolveOrCreate(Long userId, String rawName) {
        String normalized = NameNormalizer.normalized(rawName);
        for (TaxonomyService.Template template : TaxonomyService.PRESETS) {
            if (NameNormalizer.normalized(template.name()).equals(normalized)) {
                Optional<UserSubject> preset = subjectRepository.findByUserIdAndSystemKey(userId, template.systemKey());
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
        try {
            return subjectRepository.saveAndFlush(subject);
        } catch (DataIntegrityViolationException e) {
            return subjectRepository.findByUserIdAndNormalizedName(userId, normalized).orElse(null);
        }
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
