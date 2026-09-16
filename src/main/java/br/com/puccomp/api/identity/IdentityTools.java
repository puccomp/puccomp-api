package br.com.puccomp.api.identity;

import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.identity.account.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * A ferramenta de orientação: sem ela o agente não sabe a que EJ está conectado, em nome de quem,
 * nem o que pode fazer — e só descobre um limite ao esbarrar nele, na forma de um "Access Denied"
 * seco. É a única sem prefixo de módulo, porque não trata de um assunto da EJ e sim da conexão.
 */
@Component
@RequiredArgsConstructor
public class IdentityTools {

    private final AuthService service;
    private final ObjectMapper json;

    @McpTool(name = "whoami",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Descreve a conexão: a Empresa Júnior deste token, o membro em nome de quem as \
                    ferramentas rodam e as permissões efetivas. Não exige permissão nenhuma além \
                    de estar conectado.

                    Chame isto antes de concluir que um dado não existe. As permissões aqui já vêm \
                    com o escopo do token aplicado, então o que não estiver nesta lista vai ser \
                    recusado — e a recusa significa falta de acesso, nunca ausência do dado.

                    Devolve {organization, email, member, permissions}.""")
    public String whoami() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return json.writeValueAsString(service.me(
                (AuthPrincipal) authentication.getPrincipal(), authentication.getAuthorities()));
    }
}
