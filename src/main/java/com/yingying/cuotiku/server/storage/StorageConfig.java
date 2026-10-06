package com.yingying.cuotiku.server.storage;

import com.yingying.cuotiku.server.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public BookStorage bookStorage(AppProperties properties) {
        AppProperties.Cos cos = properties.cos();
        boolean bucketConfigured = cos != null && cos.bucket() != null && !cos.bucket().isBlank();
        if (bucketConfigured && !cos.cosEnabled()) {
            log.warn("已配置 COS 存储桶 {} 但密钥缺失（COS_SECRET_ID/COS_SECRET_KEY 或 TENCENT_SECRET_ID/TENCENT_SECRET_KEY），"
                    + "回退到本地存储，生产环境请注入密钥", cos.bucket());
        }
        BookStorage storage = bucketConfigured && cos.cosEnabled()
                ? new CosBookStorage(cos)
                : new LocalBookStorage(cos == null ? "data/book-storage" : cos.localDir());
        log.info("错题图片存储后端：{}", storage.describe());
        return storage;
    }
}
