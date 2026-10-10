package com.yingying.cuotiku.server.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.yingying.cuotiku.server.config.AppProperties;
import com.yingying.cuotiku.server.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CosBookStorage implements BookStorage {

    private static final Logger log = LoggerFactory.getLogger(CosBookStorage.class);

    private final COSClient client;
    private final String bucket;
    private final String region;

    public CosBookStorage(AppProperties.Cos config) {
        COSCredentials credentials = new BasicCOSCredentials(config.secretId(), config.secretKey());
        this.region = config.region();
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(com.qcloud.cos.http.HttpProtocol.https);
        this.client = new COSClient(credentials, clientConfig);
        this.bucket = config.bucket();
    }

    @Override
    public void put(String key, byte[] content) { put(key, content, "application/zip"); }

    @Override
    public void put(String key, byte[] content, String mimeType) {
        if (log.isDebugEnabled()) {
            log.debug("[COS] 上传 key={} bytes={}", key, content.length);
        }
        long startMillis = System.currentTimeMillis();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(mimeType);
            client.putObject(bucket, key, new ByteArrayInputStream(content), metadata);
            if (log.isDebugEnabled()) {
                log.debug("[COS] 上传完成 key={} 耗时={}ms", key, System.currentTimeMillis() - startMillis);
            }
        } catch (CosClientException e) {
            log.error("COS 上传失败 key={}", key, e);
            throw new ApiException(502, "错题图片上传对象存储失败，请稍后重试");
        }
    }

    @Override
    public long size(String key) {
        try { return client.getObjectMetadata(bucket, key).getContentLength(); }
        catch (CosClientException e) { throw new ApiException(502, "无法读取对象存储元数据"); }
    }

    @Override
    public String signedUrl(String key, String method, String mimeType, java.time.Instant expiresAt) {
        return signedUrl(key, method, mimeType, expiresAt, "inline");
    }

    @Override
    public String signedUrl(String key, String method, String mimeType, java.time.Instant expiresAt, String disposition) {
        com.qcloud.cos.model.GeneratePresignedUrlRequest request =
                new com.qcloud.cos.model.GeneratePresignedUrlRequest(bucket, key,
                        com.qcloud.cos.http.HttpMethodName.valueOf(method));
        request.setExpiration(java.util.Date.from(expiresAt));
        if ("PUT".equals(method)) request.setContentType(mimeType);
        else {
            com.qcloud.cos.model.ResponseHeaderOverrides headers = new com.qcloud.cos.model.ResponseHeaderOverrides();
            headers.setContentDisposition(disposition);
            request.setResponseHeaders(headers);
        }
        return client.generatePresignedUrl(request).toExternalForm();
    }

    @Override
    public byte[] get(String key) {
        if (log.isDebugEnabled()) {
            log.debug("[COS] 下载 key={}", key);
        }
        try (COSObject object = client.getObject(bucket, key);
             InputStream in = object.getObjectContent()) {
            byte[] bytes = in.readAllBytes();
            if (log.isDebugEnabled()) {
                log.debug("[COS] 下载完成 key={} bytes={}", key, bytes.length);
            }
            return bytes;
        } catch (CosServiceException e) {
            if (e.getStatusCode() == 404) {
                throw ApiException.notFound("错题图片不存在或已被删除");
            }
            log.error("COS 下载失败 key={}", key, e);
            throw new ApiException(502, "从对象存储下载错题图片失败");
        } catch (CosClientException | IOException e) {
            log.error("COS 下载失败 key={}", key, e);
            throw new ApiException(502, "从对象存储下载错题图片失败");
        }
    }

    @Override
    public void delete(String key) {
        if (log.isDebugEnabled()) {
            log.debug("[COS] 删除 key={}", key);
        }
        try {
            client.deleteObject(bucket, key);
        } catch (CosClientException e) {
            log.warn("COS 删除失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void deleteStrict(String key) {
        try { client.deleteObject(bucket, key); }
        catch (CosClientException e) { throw new ApiException(502, "对象存储删除失败"); }
    }

    @Override
    public String describe() {
        return "cos:" + bucket + "@" + region;
    }
}
