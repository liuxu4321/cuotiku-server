package com.yingying.cuotiku.server;

import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.storage.CosBookStorage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CosLiveSmokeTest {

    @Test
    void uploadDownloadDelete() {
        String secretId = configuredOrFallback("COS_SECRET_ID", "TENCENT_SECRET_ID");
        String secretKey = configuredOrFallback("COS_SECRET_KEY", "TENCENT_SECRET_KEY");
        Assumptions.assumeTrue(secretId != null && !secretId.isBlank(), "未提供腾讯云密钥，跳过真实COS冒烟");

        if (System.getenv("COS_LIST_BUCKETS") != null) {
            com.qcloud.cos.COSClient c = new com.qcloud.cos.COSClient(
                    new com.qcloud.cos.auth.BasicCOSCredentials(secretId, secretKey),
                    new com.qcloud.cos.ClientConfig(new com.qcloud.cos.region.Region("ap-guangzhou")));
            for (com.qcloud.cos.model.Bucket b : c.listBuckets()) {
                System.out.println("BUCKET " + b.getName() + " @ " + b.getLocation());
            }
            c.shutdown();
            return;
        }

        String region = System.getenv().getOrDefault("COS_REGION", "ap-guangzhou");
        String bucket = System.getenv().getOrDefault("COS_BUCKET", "cuotiji-1257458058");
        AppProperties.Cos config = new AppProperties.Cos(secretId, secretKey, region, bucket, "data/book-storage");
        CosBookStorage storage = new CosBookStorage(config);
        System.out.println("存储后端: " + storage.describe());

        String key = "smoke-test/" + UUID.randomUUID() + ".zip";
        byte[] content = ("cos-smoke-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        try {
            storage.put(key, content);
            System.out.println("上传成功: " + key);
            byte[] downloaded = storage.get(key);
            assertArrayEquals(content, downloaded);
            System.out.println("下载校验一致, " + downloaded.length + " bytes");
        } finally {
            storage.deleteStrict(key);
            System.out.println("已删除测试对象: " + key);
        }
    }

    private static String configuredOrFallback(String primary, String fallback) {
        String value = System.getenv(primary);
        return value == null || value.isBlank() ? System.getenv(fallback) : value;
    }
}
