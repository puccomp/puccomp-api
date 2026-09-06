package br.com.puccomp.api.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("puccomp.email")
record EmailProperties(String from) { }
