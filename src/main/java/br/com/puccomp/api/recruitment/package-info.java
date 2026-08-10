/**
 * Recrutamento: processos seletivos e as inscrições que chegam por eles.
 *
 * <p>Dois sub-pacotes com dependência em <b>um sentido só</b>: {@code candidacies} conhece
 * {@code processes}, nunca o contrário — uma inscrição pertence a um processo, mas um processo
 * não precisa saber que inscrições existem.
 *
 * <p>É o único módulo com superfície anônima. Ela vive sob {@code /v1/public/{ejSlug}/**} e expõe
 * apenas processos {@code OPEN}. O tenant vem do slug e é fixado pelo {@code PublicTenantFilter},
 * em {@code identity}: nenhum service daqui resolve tenant por conta própria.
 *
 * <p>Não depende de nenhum outro módulo de negócio — só de {@code shared}.
 *
 * <p><b>Pendente:</b> anexo de currículo. Aguarda a definição do módulo de arquivos; até lá a
 * inscrição carrega apenas {@code linkedinUrl} e {@code portfolioUrl}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Recruitment")
package br.com.puccomp.api.recruitment;
