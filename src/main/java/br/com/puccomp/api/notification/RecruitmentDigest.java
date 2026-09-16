package br.com.puccomp.api.notification;

import br.com.puccomp.api.identity.OrganizationDirectory;
import br.com.puccomp.api.recruitment.CandidateDirectory;
import br.com.puccomp.api.shared.tenant.OrganizationTime;
import br.com.puccomp.api.shared.text.Html;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * O resumo diário das inscrições, para a EJ que desliga o aviso por inscrição. Num dia de prazo o
 * modo imediato manda uma mensagem por inscrição para cada destinatário; aqui sai uma por dia.
 *
 * <p>Roda para todas as EJs: nenhuma requisição delimita este recorte.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecruitmentDigest {

    private static final String RECRUITMENT_READ = "recruitment:read";

    private static final Duration WINDOW = Duration.ofDays(1);

    private final CandidateDirectory candidates;
    private final TenantScope tenants;
    private final AudienceNotifier notifier;
    private final OrganizationDirectory organizations;
    private final NotificationProperties properties;

    /** De manhã, no fuso da EJ. Externalizado porque o teste desliga o agendamento com {@code -}. */
    @Scheduled(cron = "${puccomp.notification.digest-cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
    void send() {
        if (properties.newApplication() != NotificationProperties.ArrivalMode.DIGEST) return;

        Instant since = Instant.now().minus(WINDOW);
        for (var arrivals : candidates.arrivalsSince(since)) {
            try {
                tenants.run(arrivals.tenantId(), () -> notify(arrivals, since));
            } catch (RuntimeException e) {
                // Uma EJ com problema não pode calar o resumo das outras.
                log.error("Falha ao enviar o resumo de inscrições do tenant {}: {}",
                        arrivals.tenantId(), e.getMessage(), e);
            }
        }
    }

    private void notify(CandidateDirectory.TenantArrivals arrivals, Instant since) {
        notifier.notifyPermissionHolders(RECRUITMENT_READ,
                "%d %s nas últimas 24h".formatted(arrivals.total(),
                        arrivals.total() == 1 ? "nova inscrição" : "novas inscrições"),
                "resumo-inscricoes",
                Map.of(
                        "preheader", "O que chegou desde " + OrganizationTime.display(since) + ".",
                        "total", String.valueOf(arrivals.total()),
                        "organizationName", organizations.currentName(),
                        "since", OrganizationTime.display(since),
                        "processRowsHtml", rowsOf(arrivals)));
    }

    /**
     * As linhas da tabela, montadas aqui porque o template substitui marcador e não itera. O sufixo
     * {@code Html} da chave autoriza a entrada sem escape — daí escapar cada valor de dentro.
     */
    private static String rowsOf(CandidateDirectory.TenantArrivals arrivals) {
        return arrivals.byProcess().stream().map(process -> """
                <tr>
                    <td style="padding:10px 0 0 0; font-family:Geist,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; font-size:15px; line-height:22px; color:#FAFAFA; word-break:break-word;">%s</td>
                    <td align="right" style="padding:10px 0 0 0; font-family:Geist,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; font-size:15px; line-height:22px; color:#00B8DB; white-space:nowrap;">%d</td>
                </tr>
                """.formatted(Html.escape(process.title()), process.total()))
                .collect(Collectors.joining());
    }
}
