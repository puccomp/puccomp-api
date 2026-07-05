/**
 * Estrutura da EJ: {@code Member}, {@code Role} (cargo) e {@code Department}. É o único módulo com
 * FKs físicas entre suas entidades (membro→cargo, membro→departamento, departamento→cargo líder).
 * Expõe as vitrines {@code MemberDirectory}, {@code MemberProvisioning} e {@code RoleProvisioning}
 * para os demais módulos operarem sem importar as entidades internas.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organization")
package br.com.puccomp.api.organization;
