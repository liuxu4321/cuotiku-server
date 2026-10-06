package com.yingying.cuotiku.server.storage;

import com.yingying.cuotiku.server.web.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalBookStorage implements BookStorage {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalBookStorage.class);

    private final Path root;

    public LocalBookStorage(String localDir) {
        this.root = Paths.get(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地错题存储目录：" + root, e);
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw ApiException.badRequest("非法的存储路径");
        }
        return target;
    }

    @Override
    public void put(String key, byte[] content) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            if (log.isDebugEnabled()) {
                log.debug("[本地存储] 写入 {} bytes={}", target, content.length);
            }
        } catch (IOException e) {
            throw new ApiException(500, "本地存储写入失败：" + e.getMessage());
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            byte[] bytes = Files.readAllBytes(resolve(key));
            if (log.isDebugEnabled()) {
                log.debug("[本地存储] 读取 key={} bytes={}", key, bytes.length);
            }
            return bytes;
        } catch (IOException e) {
            throw ApiException.notFound("错题图片不存在或已被删除");
        }
    }

    @Override
    public void delete(String key) {
        try {
            boolean removed = Files.deleteIfExists(resolve(key));
            if (log.isDebugEnabled()) {
                log.debug("[本地存储] 删除 key={} 存在={}", key, removed);
            }
        } catch (IOException e) {
            // 删除失败不阻断业务流程，仅记录
        }
    }

    @Override
    public String describe() {
        return "local:" + root;
    }
}
