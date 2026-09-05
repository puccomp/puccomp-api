package br.com.puccomp.api.files.internal;

import java.time.Duration;

interface ObjectStorage {
    void put(String bucket, String key, byte[] content);
    String downloadUrl(String bucket, String key, String filename, Duration duration);
    void delete(String bucket, String key);
}
