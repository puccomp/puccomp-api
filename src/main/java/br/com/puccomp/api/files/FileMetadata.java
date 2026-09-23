package br.com.puccomp.api.files;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** O que foi anexado, sem dar acesso ao conteúdo. O acesso é um {@link FileDownload}, pedido à parte. */
public record FileMetadata(UUID id, String filename,
                           @Schema(name = "content_type") String contentType, long size) { }
