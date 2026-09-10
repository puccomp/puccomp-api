/**
 * Massa de demonstração do perfil {@code dev}. Não é módulo de negócio.
 *
 * <p>Escreve por SQL porque a massa precisa de datas passadas, e nenhuma API de escrita aceita —
 * nem deveria aceitar — um instante de ontem. O preço é conhecer o esquema: quando uma migration
 * mudar uma coluna usada aqui, o seed quebra no boot do perfil dev.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Dev Seed")
package br.com.puccomp.api.dev;
