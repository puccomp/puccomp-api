package br.com.puccomp.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @Async} vale para a aplicação inteira, então liga aqui e não dentro de um módulo. */
@Configuration
@EnableAsync
class AsyncConfig { }
