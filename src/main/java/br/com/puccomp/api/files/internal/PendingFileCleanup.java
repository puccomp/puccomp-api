package br.com.puccomp.api.files.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "puccomp.files", name = "enabled", havingValue = "true")
class PendingFileCleanup {
    private static final Logger log = LoggerFactory.getLogger(PendingFileCleanup.class);
    private final FileRepository repository;
    private final ObjectStorage storage;

    PendingFileCleanup(FileRepository repository, ObjectStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    @Transactional
    public void removeExpired() {
        for (var file : repository.expired()) {
            try {
                storage.delete(file.bucket(), file.objectKey());
            } catch (RuntimeException e) {
                log.warn("Não foi possível limpar o arquivo pendente {}; será tentado novamente", file.id());
                continue;
            }
            repository.deletePending(file);
        }
    }
}
