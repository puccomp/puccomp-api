package br.com.puccomp.api.files.internal;

import br.com.puccomp.api.shared.exception.InvalidFileException;
import br.com.puccomp.api.shared.exception.ServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
class ClamAvScanner {
    private final FileProperties properties;

    ClamAvScanner(FileProperties properties) { this.properties = properties; }

    void scan(byte[] bytes) {
        // O prazo cobre conexão, escrita e leitura. SO_TIMEOUT sozinho não limita a escrita.
        try (var socket = new Socket()) {
            var task = new FutureTask<String>(() -> exchange(socket, bytes));
            Thread.ofVirtual().start(task);
            try {
                String verdict = task.get(20, TimeUnit.SECONDS);
                if (verdict.startsWith("stream: ") && verdict.endsWith(" FOUND"))
                    throw new InvalidFileException("O arquivo foi recusado pela verificação de segurança");
                if (!"stream: OK".equals(verdict))
                    throw unavailable();
            } finally {
                task.cancel(true);
            }
        } catch (IOException | ExecutionException | TimeoutException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private String exchange(Socket socket, byte[] bytes) throws IOException {
        socket.connect(new InetSocketAddress(properties.clamavHost(), properties.clamavPort()), 3000);
        socket.setSoTimeout(15000);
        var output = new DataOutputStream(socket.getOutputStream());
        output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
        for (int offset = 0; offset < bytes.length; offset += 8192) {
            int length = Math.min(8192, bytes.length - offset);
            output.writeInt(length);
            output.write(bytes, offset, length);
        }
        output.writeInt(0);
        output.flush();
        var input = socket.getInputStream();
        var response = new ByteArrayOutputStream();
        for (int count = 0; count < 1024; count++) {
            int value = input.read();
            if (value == 0) return response.toString(StandardCharsets.UTF_8);
            if (value == -1) throw new IOException("Resposta incompleta do scanner");
            response.write(value);
        }
        throw new IOException("Resposta excessiva do scanner");
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("Verificação de segurança indisponível; tente novamente mais tarde");
    }
}
