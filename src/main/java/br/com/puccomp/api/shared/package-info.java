/**
 * Tipos de apoio compartilhados (auditoria, exceções, referências, contexto de tenant, utilidades),
 * sem regra de negócio. Módulo {@code OPEN}: qualquer módulo pode depender dele.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package br.com.puccomp.api.shared;
