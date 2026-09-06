package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.shared.exception.InvalidFileException;
import br.com.puccomp.api.shared.exception.ServiceUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ClamAvScannerTest {
    @ParameterizedTest
    @ValueSource(strings = {"stream: OK", "stream: Eicar-Signature FOUND", "stream: scan failed ERROR", "garbage"})
    @DisplayName("transmite bytes pelo INSTREAM e só libera resposta positiva explícita")
    void obeysScannerVerdict(String verdict) throws Exception {
        byte[] bytes = new byte[20000];
        new java.util.Random(1).nextBytes(bytes);
        try (var server = new ServerSocket(0)) {
            var peer = new FutureTask<byte[]>(() -> {
                try (var connection = server.accept()) {
                    connection.setSoTimeout(3000);
                    var input = new DataInputStream(connection.getInputStream());
                    assertThat(new String(input.readNBytes(10), StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
                    var received = new ByteArrayOutputStream();
                    int length;
                    while ((length = input.readInt()) != 0) received.write(input.readNBytes(length));
                    connection.getOutputStream().write((verdict + "\0").getBytes(StandardCharsets.UTF_8));
                    return received.toByteArray();
                }
            });
            Thread.ofVirtual().start(peer);
            var properties = new FileProperties(true, "bucket", "sa-east-1", null, false, 300, "localhost", server.getLocalPort(),
                    20, org.springframework.util.unit.DataSize.ofMegabytes(32), org.springframework.util.unit.DataSize.ofMegabytes(256));
            var scanner = new ClamAvScanner(properties);
            if (verdict.equals("stream: OK")) assertThatCode(() -> scanner.scan(bytes)).doesNotThrowAnyException();
            else if (verdict.endsWith("FOUND")) assertThatExceptionOfType(InvalidFileException.class).isThrownBy(() -> scanner.scan(bytes));
            else assertThatThrownBy(() -> scanner.scan(bytes)).isInstanceOf(ServiceUnavailableException.class);
            assertThat(peer.get(5, TimeUnit.SECONDS)).isEqualTo(bytes);
        }
    }
}
