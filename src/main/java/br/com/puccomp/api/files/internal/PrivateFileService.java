package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.files.FileDownload;
import br.com.puccomp.api.files.FileService;
import br.com.puccomp.api.files.FileUpload;
import br.com.puccomp.api.shared.exception.ServiceUnavailableException;
import br.com.puccomp.api.shared.tenant.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
class PrivateFileService implements FileService {
    private final FileRepository repository;
    private final PdfValidator validator;
    private final ClamAvScanner scanner;
    private final FileProperties properties;
    private final ObjectProvider<ObjectStorage> storage;
    private final Semaphore uploads = new Semaphore(4);

    PrivateFileService(FileRepository repository, PdfValidator validator, ClamAvScanner scanner,
                       FileProperties properties, ObjectProvider<ObjectStorage> storage) {
        this.repository = repository;
        this.validator = validator;
        this.scanner = scanner;
        this.properties = properties;
        this.storage = storage;
    }

    @Override
    public UUID stage(FileUpload upload) {
        UUID tenant = tenant();
        ObjectStorage objects = objects();
        if (!uploads.tryAcquire())
            throw new ServiceUnavailableException("Muitos arquivos em processamento; tente novamente mais tarde");
        try {
            String filename = validator.filename(upload);
            byte[] bytes = validator.read(upload);
            scanner.scan(bytes);
            validator.validate(bytes);
            UUID id = UUID.randomUUID();
            String key = tenant + "/files/" + id + ".pdf";
            var file = new FileRepository.StoredFile(id, tenant, filename, "application/pdf", bytes.length,
                    properties.bucket(), key);
            repository.reserve(file);
            objects.put(file.bucket(), key, bytes);
            return id;
        } finally {
            uploads.release();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirm(UUID fileId) {
        repository.ready(fileId, tenant());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, FileDownload> downloads(Collection<UUID> fileIds) {
        UUID tenant = tenant();
        if (fileIds.isEmpty()) return Map.of();
        var ids = new HashSet<>(fileIds);
        var files = repository.readyFiles(ids, tenant);
        if (files.isEmpty()) return Map.of();
        ObjectStorage objects = objects();
        Duration ttl = Duration.ofSeconds(properties.downloadTtlSeconds());
        Instant expiresAt = Instant.now().plus(ttl);
        Map<UUID, FileDownload> result = new HashMap<>();
        for (var file : files) {
            String url = objects.downloadUrl(file.bucket(), file.objectKey(), file.filename(), ttl);
            result.put(file.id(), new FileDownload(file.id(), file.filename(), file.contentType(), file.size(),
                    url, expiresAt));
        }
        return Map.copyOf(result);
    }

    private ObjectStorage objects() {
        var value = storage.getIfAvailable();
        if (!properties.enabled() || value == null)
            throw new ServiceUnavailableException("Armazenamento de arquivos não está habilitado");
        return value;
    }

    private static UUID tenant() {
        UUID tenant = TenantContext.get();
        if (tenant == null) throw new IllegalStateException("Operação de arquivo exige tenant");
        return tenant;
    }
}
