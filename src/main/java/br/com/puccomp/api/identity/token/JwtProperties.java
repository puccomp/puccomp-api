package br.com.puccomp.api.identity.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("puccomp.security.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) { }
