package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.shared.exception.ServiceUnavailableException;
import software.amazon.awssdk.core.exception.SdkException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;

final class S3ObjectStorage implements ObjectStorage, AutoCloseable {
    private final S3Client client;
    private final S3Presigner signer;

    S3ObjectStorage(S3Client client, S3Presigner signer) {
        this.client = client;
        this.signer = signer;
    }

    @Override
    public void put(String bucket, String key, byte[] content) {
        try {
            client.putObject(r -> r.bucket(bucket).key(key).contentType("application/pdf")
                    .contentDisposition("attachment").cacheControl("private, no-store"), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw unavailable();
        }
    }

    @Override
    public String downloadUrl(String bucket, String key, String filename, Duration duration) {
        // O nome já passou pela allowlist; nenhum caminho, aspas ou quebra de linha chega aqui.
        var request = GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentType("application/octet-stream")
                .responseContentDisposition(ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .responseCacheControl("private, no-store").build();
        try {
            return signer.presignGetObject(r -> r.signatureDuration(duration).getObjectRequest(request))
                    .url().toExternalForm();
        } catch (SdkException e) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            client.deleteObject(r -> r.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw unavailable();
        }
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("Armazenamento indisponível; tente novamente mais tarde");
    }

    @Override
    public void close() {
        client.close();
        signer.close();
    }
}
