package com.yingying.cuotiku.server.config;

import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.service.TaxonomyMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 独立 Bean 触发回填，保证经过事务代理（服务内自调用会绕过 @Transactional）。
 */
@Configuration
public class TaxonomyMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyMigrationRunner.class);

    @Bean
    public ApplicationRunner taxonomyBackfillRunner(TaxonomyMigrationService migrationService,
                                                    AppProperties properties) {
        return args -> {
            if (properties.taxonomy() != null && !properties.taxonomy().backfillOnStartup()) {
                log.info("[迁移] backfill-on-startup=false，跳过启动回填");
                return;
            }
            TaxonomyMigrationService.BackfillSummary summary = migrationService.backfillAll();
            if (summary.users() > 0) {
                log.info("[迁移] 历史回填完成 用户={} 题目回填={}", summary.users(), summary.entriesUpdated());
            }
        };
    }
}
