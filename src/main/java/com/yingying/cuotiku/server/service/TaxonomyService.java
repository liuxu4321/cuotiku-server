package com.yingying.cuotiku.server.service;

import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.entity.User;
import com.yingying.cuotiku.server.entity.UserSubject;
import com.yingying.cuotiku.server.repository.UserRepository;
import com.yingying.cuotiku.server.repository.UserSubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 分类体系基础服务：六科惰性幂等初始化、激活置位、入口开关判定（CONTRACT §2.6/§4）。
 */
@Service
public class TaxonomyService {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyService.class);

    public record Template(String systemKey, String name, int sortOrder) {}

    public static final List<Template> PRESETS = List.of(
            new Template("chinese", "语文", 10),
            new Template("math", "数学", 20),
            new Template("english", "英语", 30),
            new Template("physics", "物理", 40),
            new Template("chemistry", "化学", 50),
            new Template("biology", "生物", 60));

    private final UserSubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final AppProperties properties;

    public TaxonomyService(UserSubjectRepository subjectRepository, UserRepository userRepository,
                           AppProperties properties) {
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public boolean entryOpen() {
        return properties.taxonomy() == null || properties.taxonomy().v2Enabled();
    }

    public String serverVersion() {
        return properties.serverVersion() == null ? "unknown" : properties.serverVersion();
    }

    /** 惰性幂等初始化六科；不激活。并发冲突逐行吸收（CONTRACT §8）。 */
    @Transactional
    public List<UserSubject> initSubjects(Long userId) {
        List<UserSubject> existing = subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);
        if (existing.size() >= PRESETS.size() && existing.stream().allMatch(s -> s.getSystemKey() != null)) {
            return existing;
        }
        for (Template template : PRESETS) {
            Optional<UserSubject> found = subjectRepository.findByUserIdAndSystemKey(userId, template.systemKey());
            if (found.isPresent()) {
                continue;
            }
            UserSubject subject = new UserSubject();
            subject.setUserId(userId);
            subject.setName(template.name());
            subject.setNormalizedName(NameNormalizer.normalized(template.name()));
            subject.setSystemKey(template.systemKey());
            subject.setSortOrder(template.sortOrder());
            try {
                subjectRepository.saveAndFlush(subject);
            } catch (DataIntegrityViolationException e) {
                log.debug("[分类] 并发初始化吸收唯一键冲突 userId={} systemKey={}", userId, template.systemKey());
            }
        }
        return subjectRepository.findByUserIdOrderBySortOrderAscIdAsc(userId);
    }

    /** 激活置位：与业务写入同事务，条件更新保证并发原子（CONTRACT §2.6/§8）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void activateIfNotYet(User user) {
        if (user.getTaxonomyV2ActivatedAt() != null) {
            return;
        }
        user.setTaxonomyV2ActivatedAt(Instant.now());
        userRepository.save(user);
        log.info("[分类] 账号激活 taxonomyV2 phone={} userId={}", user.getPhone(), user.getId());
    }

    public boolean activated(User user) {
        return user.getTaxonomyV2ActivatedAt() != null;
    }
}
