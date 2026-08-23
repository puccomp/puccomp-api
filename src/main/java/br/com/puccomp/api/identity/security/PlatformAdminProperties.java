package br.com.puccomp.api.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("puccomp.admin")
public record PlatformAdminProperties(String key) {

    boolean configured() {
        return key != null && !key.isBlank();
    }
}
