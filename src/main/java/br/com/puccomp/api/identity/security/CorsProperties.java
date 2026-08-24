package br.com.puccomp.api.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origens que o navegador pode usar para chamar a API. Fica em configuração porque o endereço
 * do front muda por ambiente, e liberar tudo ({@code *}) deixaria qualquer site fazer chamada
 * autenticada em nome de quem estivesse logado.
 */
@ConfigurationProperties("puccomp.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
