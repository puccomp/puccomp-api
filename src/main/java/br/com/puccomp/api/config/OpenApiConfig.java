package br.com.puccomp.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String PLATFORM_KEY_SCHEME = "platformKey";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PUC COMP API")
                        .description("Plataforma ERP para Empresas Juniores\n\n"
                                + "### Paginação\n"
                                + "Endpoints paginados aceitam os parâmetros `page`, `size` e `sort`. "
                                + "Tamanho padrão de página: 20, tamanho máximo aceito: 100.\n\n"
                                + "### Autenticação\n"
                                + "`Authorization: Bearer <token>` aceita duas credenciais: o JWT de sessão "
                                + "devolvido por `POST /v1/auth/login` e um PAT (`pat_...`), que é sempre "
                                + "somente-leitura. As rotas de plataforma (`/v1/admin/**`) usam o header "
                                + "`x-puccomp-key`.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT de sessão ou PAT (`pat_...`)."))
                        .addSecuritySchemes(PLATFORM_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("x-puccomp-key")
                                .description("Chave de plataforma; provisiona EJs.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
