/**
 * Estrutura da EJ: {@code Member}, {@code Role} (cargo), {@code Department} e {@code Course} (curso
 * que a EJ aceita). É o único módulo com FKs físicas entre suas entidades (membro→cargo,
 * membro→departamento, membro→curso, departamento→cargo líder). Expõe as vitrines
 * {@code MemberDirectory}, {@code MemberProvisioning}, {@code RoleProvisioning}, {@code CourseCatalog}
 * e {@code CourseProvisioning} para os demais módulos operarem sem importar as entidades internas.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organization")
package br.com.puccomp.api.organization;
