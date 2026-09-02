/**
 * Email transacional. É o dono dos templates e da redação: os outros módulos entregam
 * {@link br.com.puccomp.api.email.EmailMessage dados} — nunca HTML — então mudar a arte de um
 * email não encosta em regra de negócio, e a mesma identidade visual vale para todos os envios.
 *
 * <p>Não depende de nenhum módulo de negócio. A entrega é uma porta ({@code Mailer}) com um
 * adaptador SMTP, o que faz mailpit no dev e SES em produção serem o mesmo caminho de código,
 * trocando só configuração.
 *
 * <p>Falha de entrega não derruba a operação que a originou — ver {@code SmtpMailer}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Email")
package br.com.puccomp.api.email;
