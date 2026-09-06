/**
 * Autorização fine-grained: catálogo de permissões ({@code Permission}) e concessões por membro
 * ({@code MemberPermission}) e por cargo ({@code RolePermission}). Expõe {@code PermissionResolver},
 * dono único da precedência de acesso — alumni só leem, o dono pode tudo, o resto é a união do cargo
 * com os grants individuais — tanto para um membro por request quanto para um lote inteiro em duas
 * consultas. Referencia {@code Member} e {@code Role} por id (referências soltas) — ambos pertencem
 * a {@code organization}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Authorization")
package br.com.puccomp.api.authorization;
