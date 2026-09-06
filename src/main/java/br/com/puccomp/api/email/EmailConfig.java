package br.com.puccomp.api.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfig {

    /**
     * Pool dedicado ao SMTP: sem ele o envio ocuparia a thread da request. Publicar um {@code Executor}
     * faria o {@code applicationTaskExecutor} do Boot desaparecer — daí o {@code mode: force} no yaml.
     */
    @Bean
    TaskExecutor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }
}
