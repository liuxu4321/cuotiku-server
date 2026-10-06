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
    private final org.springframework.beans.factory.ObjectProvider<TaxonomyService> self;

    public TaxonomyService(UserSubjectRepository subjectRepository, UserRepository userRepository,
                           AppProperties properties,
                           org.springframework.beans.factory.ObjectProvider<TaxonomyService> self) {
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.self = self;
    }

    public boolean entryOpen() {
        return properties.taxonomy() == null || properties.taxonomy().v2Enabled();
    }

    public String serverVersion() {
        return properties.serverVersion() == null ? "unknown" : properties.serverVersion();
    }

    /**
     * 惰性幂等初始化六科；不激活。每个科目独立事务（REQUIRES_NEW），
     * 唯一键冲突逐行吸收、死锁整科重试（CONTRACT §8）；失败不污染外层会话。
     */
    public List<UserSubject> initSubjectsWithRetry(Long userId) {
        for (Template template : PRESETS) {
            for (int attempt = 0; attempt < 3; attempt += 1) {
                try {
                    self.getObject().initOne(userId, template);
                    break;
                } catch (DataIntegrityViolationException e) {
                    log.debug("[分类] 并发初始化吸收唯一键冲突 userId={} systemKey={}",
                            userId, template.systemKey());
                    break;
                } catch (org.springframework.dao.DeadlockLoserDataAccessException
                         | org.springframework.dao.CannotAcquireLockException e) {
                    log.debug("[分类] 并发初始化锁冲突重试 userId={} systemKey={} attempt={}",
                            userId, template.systemKey(), attempt);
                    if (attempt == 2) {
                        throw e;
                    }
                }
            }
        }
        return self.getObject().findAll(userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initOne(Long userId, Template template) {
        if (subjectRepository.findByUserIdAndSystemKey(userId, template.systemKey()).isPresent()) {
            return;
        }
        UserSubject subject = new UserSubject();
        subject.setUserId(userId);
        subject.setName(template.name());
        subject.setNormalizedName(NameNormalizer.normalized(template.name()));
        subject.setSystemKey(template.systemKey());
        subject.setSortOrder(template.sortOrder());
        subjectRepository.saveAndFlush(subject);
    }

    @Transactional(readOnly = true)
    public List<UserSubject> findAll(Long userId) {
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
