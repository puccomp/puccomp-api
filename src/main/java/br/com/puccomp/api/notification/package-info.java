/**
 * Entrega de avisos internos para o público certo dentro da EJ. Junta as três peças que a resposta
 * "quem deve receber isto?" exige — o quadro de membros ({@code organization}), as permissões
 * efetivas ({@code authorization}) e o e-mail da conta ({@code identity}) — para que os módulos de
 * negócio dependam só de {@code AudienceNotifier} e não remontem essa decisão cada um do seu jeito.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package br.com.puccomp.api.notification;
