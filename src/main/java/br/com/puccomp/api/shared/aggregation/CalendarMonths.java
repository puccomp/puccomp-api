package br.com.puccomp.api.shared.aggregation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mês civil médio, para converter duração em meses sem presumir 30 dias. Mora aqui porque o
 * relatório histórico e o resumo do quadro precisam da mesma unidade — duas constantes com valores
 * ligeiramente diferentes fariam "tempo de casa" e "permanência média" divergirem por arredondamento.
 */
public final class CalendarMonths {

    private static final BigDecimal DAYS = new BigDecimal("365.2425")
            .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);

    public static final BigDecimal SECONDS = DAYS.multiply(BigDecimal.valueOf(86_400));

    public static final int SCALE = 2;

    private CalendarMonths() { }

    public static BigDecimal of(long seconds) {
        return BigDecimal.valueOf(seconds).divide(SECONDS, 12, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}
