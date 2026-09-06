package br.com.puccomp.api.files;

import java.io.IOException;
import java.io.InputStream;

/** A implementação fornece um stream novo; o módulo files é responsável por fechá-lo. */
public record FileUpload(String filename, String contentType, long size, Content content) {

    @FunctionalInterface
    public interface Content {
        InputStream open() throws IOException;
    }
}
