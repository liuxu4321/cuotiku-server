package com.yingying.cuotiku.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, Captcha captcha, Ai ai, Cos cos, Agent agent) {

    public record Agent(long dailyLimit) {
        public Agent {
            if (dailyLimit < 0) dailyLimit = 20;
        }
    }

    public record Jwt(String secret, long ttlHours, long refreshTtlDays) {
        public Jwt {
            if (ttlHours <= 0) ttlHours = 2;
            if (refreshTtlDays <= 0) refreshTtlDays = 30;
        }
    }

    public record Admin(String phone, String password) {}

    public record Captcha(int ttlSeconds, int length) {
        public Captcha {
            if (ttlSeconds <= 0) ttlSeconds = 300;
            if (length <= 0) length = 4;
        }
    }

    public record Cos(String secretId, String secretKey, String region, String bucket, String localDir) {
        public Cos {
            if (region == null || region.isBlank()) region = "ap-beijing";
            if (localDir == null || localDir.isBlank()) localDir = "data/book-storage";
        }

        public boolean cosEnabled() {
            return bucket != null && !bucket.isBlank()
                    && secretId != null && !secretId.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }

    public record Ai(Tencent tencent) {
        public record Tencent(
                String endpoint, String region, String secretId, String secretKey, int timeoutSeconds) {
            public Tencent {
                if (endpoint == null || endpoint.isBlank()) endpoint = "ocr.tencentcloudapi.com";
                if (region == null || region.isBlank()) region = "ap-guangzhou";
                if (timeoutSeconds <= 0) timeoutSeconds = 90;
            }

            public boolean configured() {
                return secretId != null && !secretId.isBlank()
                        && secretKey != null && !secretKey.isBlank();
            }
        }
    }
}
