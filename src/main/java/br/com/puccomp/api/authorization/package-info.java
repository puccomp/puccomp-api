/**
 * Autorização fine-grained: catálogo de permissões ({@code Permission}) e concessões por membro
 * ({@code MemberPermission}) e por cargo ({@code RolePermission}). Expõe {@code PermissionResolver},
 * que resolve as autoridades efetivas de um membro a cada request. Referencia {@code Member} e
 * {@code Role} por id (referências soltas) — ambos pertencem a {@code organization}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Authorization")
package br.com.puccomp.api.authorization;
