package br.com.puccomp.api.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender javaMailSender, 
                        @Value("${puccomp.onboarding.from-address}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
    }

    public void enviarConfirmacaoInscricao(String emailDestino, String nomeCandidato) {
        try {
            MimeMessage mensagem = javaMailSender.createMimeMessage();
            // O 'true' avisa que vamos usar HTML
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
            
            helper.setFrom(fromAddress);
            helper.setTo(emailDestino);
            helper.setSubject("Inscrição Confirmada - Processo Seletivo COMP");
            
            // Monta o HTML formatado com o nome do candidato
            String conteudoHtml = String.format(obterTemplateHtml(), nomeCandidato);
            
            // O segundo parâmetro 'true' avisa o Spring para renderizar como HTML
            helper.setText(conteudoHtml, true); 

            javaMailSender.send(mensagem);
            
        } catch (MessagingException e) {
            throw new RuntimeException("Falha ao enviar e-mail de confirmação", e);
        }
    }
        
    private String obterTemplateHtml() {
        return """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
            <meta charset="UTF-8">
            <style>
                /* Reset básico */
                body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #121212; color: #E2E8F0; }
                
                /* Container principal imitando o fundo do site */
                .container { max-width: 600px; margin: 40px auto; background-color: #1A1C23; border: 1px solid #2D3748; border-radius: 8px; overflow: hidden; }
                
                /* Cabeçalho */
                .header { background-color: #121212; padding: 30px 20px; text-align: center; border-bottom: 1px solid #2D3748; }
                
                /* IMPORTANTE: Troque o src abaixo pelo link real da logo da COMP hospedada no seu site */
                .header img { max-width: 150px; height: auto; }
                
                /* Se não tiver o link da logo em mãos agora, apague a tag <img> acima e descomente o <h1> abaixo */
                /* .header h1 { margin: 0; font-size: 28px; letter-spacing: 2px; color: #FFFFFF; font-family: monospace; } */

                /* Corpo do E-mail */
                .content { padding: 40px 30px; line-height: 1.6; font-size: 16px; }
                .content h2 { color: #FFFFFF; font-size: 24px; margin-top: 0; font-weight: 600; }
                
                /* Caixa de destaque amarela imitando o post-it do site */
                .highlight-box { 
                    background-color: #FDE047; /* Amarelo da COMP */
                    color: #000000; 
                    padding: 18px 20px; 
                    margin: 30px 0; 
                    font-weight: 700;
                    border: 2px solid #000000;
                    box-shadow: 4px 4px 0px #000000; /* Efeito sombra sólida */
                    border-radius: 2px;
                    text-align: center;
                    font-size: 18px;
                }
                
                /* Detalhe em ciano imitando o texto "deixa rastro" */
                .cyan-text { color: #5EEAD4; font-weight: bold; }

                /* Rodapé */
                .footer { background-color: #121212; padding: 25px 20px; text-align: center; font-size: 13px; color: #718096; border-top: 1px solid #2D3748; }
                
                /* Botão Azul vivo do site */
                .button { 
                    display: inline-block; 
                    padding: 14px 32px; 
                    background-color: #1A5CFF; /* Azul da COMP */
                    color: #FFFFFF !important; 
                    text-decoration: none; 
                    border-radius: 4px; 
                    font-weight: bold; 
                    margin-top: 20px;
                    transition: background-color 0.3s;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <!-- Coloque a URL da logo branca da COMP aqui -->
                    <img src="https://puccomp.com.br/sua-logo-aqui.png" alt="COMP" />
                </div>
                
                <div class="content">
                    <h2>Olá, %s! 🚀</h2>
                    <p>O processo seletivo da Comp está <span class="cyan-text">aberto</span> e nós recebemos a sua candidatura!</p>
                    
                    <div class="highlight-box">
                        INSCRIÇÃO CONFIRMADA ✅
                    </div>
                    
                    <p>Aqui, aprender deixa rastro. Fique de olho nesta caixa de entrada, pois em breve enviaremos novas atualizações sobre os próximos passos e desafios práticos.</p>
                    <p>Enquanto isso, que tal explorar um pouco mais sobre o que você vai viver com a gente?</p>
                    
                    <div style="text-align: center; margin-top: 40px; margin-bottom: 20px;">
                        <a href="https://puccomp.com.br" class="button">Quero ver o site</a>
                    </div>
                    
                </div>
                
                <div class="footer">
                    © 2026 Empresa Júnior de Computação<br>
                    Este é um e-mail automático. Por favor, não responda.
                </div>
            </div>
        </body>
        </html>
        """;
    }
}