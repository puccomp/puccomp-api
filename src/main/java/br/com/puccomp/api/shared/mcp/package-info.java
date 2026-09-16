/**
 * Apoio comum às ferramentas MCP.
 *
 * <p>Cada módulo expõe as suas numa única classe {@code *Tools}, no pacote raiz do módulo. A
 * superfície é snake_case nos dois sentidos, como a da API REST: na saída porque cada ferramenta
 * serializa com o {@code ObjectMapper} da aplicação e devolve {@code String} — o Spring AI usaria um
 * mapper estático próprio, que ignora a configuração do Spring; na entrada porque o nome do
 * parâmetro Java é o nome publicado no schema, daí o sublinhado nas assinaturas de ferramenta, e só
 * nelas. Ver ADR 0006.
 */
@org.springframework.modulith.NamedInterface("mcp")
package br.com.puccomp.api.shared.mcp;
