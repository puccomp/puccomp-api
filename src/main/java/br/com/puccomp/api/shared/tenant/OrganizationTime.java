package br.com.puccomp.api.shared.tenant;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * O fuso em que a EJ lê as próprias datas. Agrupar em UTC empurraria o fim da noite para o dia
 * seguinte, bem onde o pico de prazo acontece.
 *
 * <p>Constante, e não configuração por tenant: ainda não existe a primeira EJ fora deste fuso.
 * Quando existir, é aqui que ela é resolvida pelo {@link TenantContext}, sem mexer em chamador.
 */
public final class OrganizationTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private OrganizationTime() { }

    public static String display(Instant instant) {
        return instant == null ? "" : DISPLAY.format(instant.atZone(ZONE));
    }
}
