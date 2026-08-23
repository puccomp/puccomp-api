package br.com.puccomp.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PUC COMP API")
                        .description("Plataforma ERP para Empresas Juniores\n\n"
                                + "### Paginação\n"
                                + "Endpoints paginados aceitam os parâmetros `page`, `size` e `sort`. "
                                + "Tamanho padrão de página: 20, tamanho máximo aceito: 100.")
                        .version("v1"));
    }
}
