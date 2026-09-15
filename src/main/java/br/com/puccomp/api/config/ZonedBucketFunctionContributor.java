package br.com.puccomp.api.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registra {@code day_in_zone(instante, fuso)}, {@code month_in_zone(instante, fuso)} e
 * {@code month_of(data)} para agrupar séries temporais.
 *
 * <p>Agrupar em UTC jogaria toda a noite para o dia seguinte, justamente onde o pico de prazo
 * acontece. A conversão fica no banco para a consulta continuar em Criteria — e, com ela, o filtro
 * de tenant do Hibernate. O {@code cast} explícito do fuso evita a ambiguidade do Postgres entre
 * {@code at time zone text} e {@code at time zone interval} quando o valor chega como parâmetro.
 *
 * <p>A série mensal devolve o primeiro dia do mês local, e não um par ano/mês, para a ordem
 * cronológica ser a ordem natural da data.
 *
 * <p>{@code month_of} é a versão sem fuso, para coluna que já é {@code date}.
 */
public class ZonedBucketFunctionContributor implements FunctionContributor {

    private static final String IN_ZONE = "(?1 at time zone cast(?2 as text))";

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        register(functions, "day_in_zone", "cast(" + IN_ZONE + " as date)");
        register(functions, "month_in_zone", "cast(date_trunc('month', " + IN_ZONE + ") as date)");
        register(functions, "month_of", "cast(date_trunc('month', ?1) as date)", 1);
    }

    private static void register(FunctionContributions functions, String name, String pattern) {
        register(functions, name, pattern, 2);
    }

    private static void register(FunctionContributions functions, String name, String pattern,
                                 int arguments) {
        functions.getFunctionRegistry()
                .patternDescriptorBuilder(name, pattern)
                .setExactArgumentCount(arguments)
                .setInvariantType(functions.getTypeConfiguration().getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.LOCAL_DATE))
                .register();
    }
}
