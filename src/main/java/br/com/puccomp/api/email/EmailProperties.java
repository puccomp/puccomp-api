package br.com.puccomp.api.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from remetente de todo email transacional. Em produção precisa ser um endereço verificado
 *             no SES, senão a entrega é recusada.
 */
@ConfigurationProperties("puccomp.email")
record EmailProperties(String from) { }
