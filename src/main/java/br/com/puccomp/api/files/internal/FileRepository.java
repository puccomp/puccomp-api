package br.com.puccomp.api.files.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class FileRepository {
    record StoredFile(UUID id, UUID tenantId, String filename, String contentType, long size,
                      String bucket, String objectKey) { }

    private final JdbcClient jdbc;
    private final TransactionTemplate independent;

    FileRepository(JdbcClient jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void reserve(StoredFile file) {
        // Transação própria: a reserva precisa sobreviver mesmo se o consumidor abortar depois do PUT.
        independent.executeWithoutResult(status -> jdbc.sql("""
                insert into stored_files (id, tenant_id, filename, content_type, size, bucket, object_key, state)
                values (:id, :tenant, :filename, :type, :size, :bucket, :key, 'PENDING')
                """).param("id", file.id()).param("tenant", file.tenantId()).param("filename", file.filename())
                .param("type", file.contentType()).param("size", file.size()).param("bucket", file.bucket())
                .param("key", file.objectKey()).update());
    }

    void ready(UUID id, UUID tenantId) {
        int updated = jdbc.sql("""
                update stored_files set state = 'READY' where id = :id and tenant_id = :tenant and state = 'PENDING'
                """).param("id", id).param("tenant", tenantId).update();
        if (updated != 1) throw new IllegalStateException("Reserva de arquivo não encontrada");
    }

    List<StoredFile> readyFiles(Collection<UUID> ids, UUID tenantId) {
        return jdbc.sql("""
                select id, tenant_id, filename, content_type, size, bucket, object_key
                from stored_files where tenant_id = :tenant and id in (:ids) and state = 'READY'
                """).param("tenant", tenantId).param("ids", ids).query(StoredFile.class).list();
    }

    /** Manutenção global, sem requisição de usuário: bloqueia somente reservas expiradas. */
    List<StoredFile> expired() {
        return jdbc.sql("""
                select id, tenant_id, filename, content_type, size, bucket, object_key from stored_files
                where state = 'PENDING' and created_at < now() - interval '24 hours'
                order by created_at limit 20 for update skip locked
                """).query(StoredFile.class).list();
    }

    void deletePending(StoredFile file) {
        jdbc.sql("delete from stored_files where id = :id and tenant_id = :tenant and state = 'PENDING'")
                .param("id", file.id()).param("tenant", file.tenantId()).update();
    }
}
