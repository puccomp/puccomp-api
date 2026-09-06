package br.com.puccomp.api.files;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record FileDownload(UUID id, String filename,
                           @Schema(name = "content_type") String contentType, long size,
                           @Schema(name = "download_url") String downloadUrl,
                           @Schema(name = "download_expires_at") Instant downloadExpiresAt) { }
