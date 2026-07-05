/**
 * Autenticação e identidade: {@code Account} (identidade global — login reutilizável entre EJs),
 * {@code Tenant}, login por JWT, PAT e convites de onboarding. O vínculo de uma conta com uma EJ mora
 * no {@code Member} (que aponta pra conta por {@code account_id}); no login, resolve o vínculo ativo via
 * {@code MemberDirectory}. Emite e valida tokens; não conhece cargos nem permissões: a resolução de
 * acesso é responsabilidade de {@code authorization}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package br.com.puccomp.api.identity;
