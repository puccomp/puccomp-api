package br.com.puccomp.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injetado, e não {@code Instant.now()}: virada de mês precisa ser testável sem esperar. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
