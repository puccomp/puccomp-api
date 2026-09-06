package br.com.puccomp.api.files.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("puccomp.files")
record FileProperties(boolean enabled, String bucket, @NotBlank String region,
                      String endpoint, boolean pathStyle,
                      @Min(1) @Max(900) int downloadTtlSeconds,
                      @NotBlank String clamavHost, @Min(1) @Max(65535) int clamavPort,
                      @Min(1) @Max(100) int maxPages,
                      @NotNull DataSize decompressedPerPage, @NotNull DataSize maxDecompressed) {
    @AssertTrue(message = "FILES_BUCKET é obrigatório quando o armazenamento está habilitado")
    public boolean isBucketConfigured() {
        return !enabled || bucket != null && !bucket.isBlank();
    }

    @AssertTrue(message = "decompressed-per-page e max-decompressed precisam ser positivos")
    public boolean isDecompressionBudgetPositive() {
        return decompressedPerPage.toBytes() > 0 && maxDecompressed.toBytes() > 0;
    }

    /** Orçamento de inflação proporcional às páginas reais, com teto absoluto contra zip bomb. */
    long decompressionBudget(int pages) {
        return Math.min(pages * decompressedPerPage.toBytes(), maxDecompressed.toBytes());
    }
}
