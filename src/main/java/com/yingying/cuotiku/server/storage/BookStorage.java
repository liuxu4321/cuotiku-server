package com.yingying.cuotiku.server.storage;

public interface BookStorage {

    void put(String key, byte[] content);

    /** 新资产按真实MIME写入；旧错题ZIP接口继续使用原put。 */
    default void put(String key, byte[] content, String mimeType) { put(key, content); }

    default long size(String key) { return get(key).length; }

    default String signedUrl(String key, String method, String mimeType, java.time.Instant expiresAt) { return null; }

    default String signedUrl(String key, String method, String mimeType, java.time.Instant expiresAt, String disposition) { return signedUrl(key, method, mimeType, expiresAt); }

    byte[] get(String key);

    void delete(String key);

    default void deleteStrict(String key) { delete(key); }

    String describe();
}
