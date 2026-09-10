package br.com.puccomp.api.organization.members.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface MemberStatusEventRepository extends JpaRepository<MemberStatusEvent, UUID> {

    @Query("select coalesce(max(e.sequence), 0) from MemberStatusEvent e where e.memberId = :memberId")
    long lastSequenceOf(@Param("memberId") UUID memberId);

    /**
     * O histórico inteiro da EJ, em ordem de vínculo. Uma EJ tem dezenas a poucas centenas de
     * membros e uns poucos eventos cada: reconstruir os intervalos em memória é mais simples de
     * verificar do que espalhar a mesma definição de "intervalo ativo" por várias consultas — e é
     * a mesma leitura que alimenta janela atual, janela anterior e coortes.
     */
    @Query("select e from MemberStatusEvent e order by e.memberId asc, e.sequence asc")
    List<MemberStatusEvent> findAllOrdered();
}
