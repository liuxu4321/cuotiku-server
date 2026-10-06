package com.yingying.cuotiku.server.storage;

public interface BookStorage {

    void put(String key, byte[] content);

    byte[] get(String key);

    void delete(String key);

    String describe();
}
