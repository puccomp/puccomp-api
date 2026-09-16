package br.com.puccomp.api.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param newApplication como a equipe recebe as inscrições que chegam
 * @param invitationReminder antecedência com que o convite pendente é lembrado a quem convidou
 */
@ConfigurationProperties("puccomp.notification")
record NotificationProperties(ArrivalMode newApplication, Duration invitationReminder) {

    NotificationProperties {
        newApplication = newApplication == null ? ArrivalMode.IMMEDIATE : newApplication;
        invitationReminder = invitationReminder == null ? Duration.ofHours(24) : invitationReminder;
    }

    enum ArrivalMode {

        /** Um aviso por inscrição, para cada pessoa com permissão de leitura no recrutamento. */
        IMMEDIATE,

        /** Um resumo por dia. O modo para EJ com volume — ver {@link RecruitmentDigest}. */
        DIGEST
    }
}
