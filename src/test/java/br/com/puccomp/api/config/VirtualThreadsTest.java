package br.com.puccomp.api.config;

import br.com.puccomp.api.support.AbstractIntegrationTest;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code spring.threads.virtual.enabled} é uma linha de yaml que some sem quebrar nada visível: a
 * API continua respondendo, só volta a ocupar uma thread de sistema por requisição parada em I/O.
 * O teste existe para que apagá-la falhe aqui, e não em produção sob rajada de chamadas de agente.
 */
class VirtualThreadsTest extends AbstractIntegrationTest {

    @Autowired
    private WebServerApplicationContext context;

    @Test
    @DisplayName("o Tomcat atende requisição em virtual thread")
    void shouldServeRequestsOnVirtualThreads() throws Exception {
        Executor executor = tomcat().getConnector().getProtocolHandler().getExecutor();

        var virtual = new CompletableFuture<Boolean>();
        executor.execute(() -> virtual.complete(Thread.currentThread().isVirtual()));

        assertThat(virtual.get(5, TimeUnit.SECONDS))
                .as("thread que executa a requisição")
                .isTrue();
    }

    private Tomcat tomcat() {
        return ((TomcatWebServer) context.getWebServer()).getTomcat();
    }
}
