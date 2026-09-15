/**
 * Todo aviso que a plataforma manda por e-mail, e a decisão de quem deve recebê-lo.
 *
 * <p>Escuta os fatos que os módulos de negócio publicam em vez de ser chamado por eles: um aviso
 * novo se escreve inteiro aqui, sem tocar no módulo que produziu o fato. Ver ADR 0005.
 *
 * <p>Junta as três peças que a pergunta sobre quem deve receber exige — quadro de membros
 * ({@code organization}), permissões efetivas ({@code authorization}) e e-mail da conta
 * ({@code identity}) — e é o único lugar onde essa decisão é tomada.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package br.com.puccomp.api.notification;
