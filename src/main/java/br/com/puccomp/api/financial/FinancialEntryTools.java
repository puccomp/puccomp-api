package br.com.puccomp.api.financial;

import br.com.puccomp.api.financial.summary.FinancialSummaryResponse;
import br.com.puccomp.api.shared.mcp.ToolPage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/** Ferramentas MCP do extrato. Ficam aqui pelo motivo descrito em {@code MemberTools}. */
@Component
@RequiredArgsConstructor
public class FinancialEntryTools {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final FinancialEntryService service;

    @McpTool(name = "financial_entries_list",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Lista os lançamentos financeiros da EJ, do mais recente para o mais antigo. \
                    Exige a permissão financial:read.

                    As datas são AAAA-MM-DD e ambas inclusivas; from maior que to é recusado. \
                    Lançamento descartado fica de fora.

                    Para saldo, totais e concentração por categoria, prefira financial_summary: \
                    ele responde de uma vez o que esta listagem só responderia somando página a \
                    página.""")
    @PreAuthorize("hasAuthority('financial:read')")
    public ToolPage<FinancialEntryResponse> list(
            @McpToolParam(required = false, description = "Data inicial, inclusive") LocalDate from,
            @McpToolParam(required = false, description = "Data final, inclusive") LocalDate to,
            @McpToolParam(required = false,
                    description = "Entrada ou saída; sem ele, os dois") FinancialEntryType type,
            @McpToolParam(required = false, description = "Página, começando em 0") Integer page,
            @McpToolParam(required = false,
                    description = "Itens por página, no máximo 100; o padrão é 20") Integer size) {

        return ToolPage.of(service.findAll(from, to, type, PageRequest.of(
                page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE))));
    }

    @McpTool(name = "financial_entries_get",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = "Busca um lançamento financeiro pelo id. Exige a permissão financial:read.")
    @PreAuthorize("hasAuthority('financial:read')")
    public FinancialEntryResponse get(
            @McpToolParam(description = "Id do lançamento, como devolvido por "
                    + "financial_entries_list") UUID id) {
        return service.findById(id);
    }

    @McpTool(name = "financial_summary",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false),
            description = """
                    Retrato agregado do extrato no período: quanto entrou, quanto saiu, o resultado, \
                    em que categorias o dinheiro se concentra e como o volume evoluiu mês a mês. \
                    Exige a permissão financial:read.

                    Com from e to preenchidos, cada medida vem acompanhada do mesmo valor na janela \
                    anterior de igual largura — 30 dias se comparam com os 30 dias anteriores. Sem \
                    os dois extremos não existe janela anterior, e previous vem nulo: comparação \
                    indisponível, que é diferente de zero.""")
    @PreAuthorize("hasAuthority('financial:read')")
    public FinancialSummaryResponse summary(
            @McpToolParam(required = false, description = "Data inicial, inclusive") LocalDate from,
            @McpToolParam(required = false, description = "Data final, inclusive") LocalDate to) {
        return service.summarize(from, to);
    }
}
