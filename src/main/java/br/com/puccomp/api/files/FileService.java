package br.com.puccomp.api.files;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface FileService {

    /**
     * Valida, escaneia e grava o objeto. Deve ser chamado <b>fora</b> da transação de negócio:
     * antivírus e S3 levam dezenas de segundos e segurariam a conexão do pool. O arquivo fica
     * reservado e indisponível para download até {@link #confirm(UUID)}; reserva não confirmada
     * é recolhida pela limpeza.
     */
    UUID stage(FileUpload upload);

    /** Exige transação do consumidor; o arquivo só fica disponível se ela confirmar. */
    void confirm(UUID fileId);

    /**
     * O consumidor deve autorizar o acesso aos recursos de negócio antes de chamar este método.
     * Arquivo inexistente, de outro tenant ou ainda não confirmado simplesmente não aparece no
     * resultado: um arquivo problemático não derruba a listagem inteira do consumidor.
     */
    Map<UUID, FileDownload> downloads(Collection<UUID> fileIds);
}
