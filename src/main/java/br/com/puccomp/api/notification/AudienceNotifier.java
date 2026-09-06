package br.com.puccomp.api.notification;

import java.util.Map;

public interface AudienceNotifier {

    /**
     * Envia o template a todo membro ativo da EJ atual que tenha a permissão. Alumni ficam de fora:
     * o acesso residual de leitura serve para consultar o que já viveram, não para receber dado novo.
     */
    void notifyPermissionHolders(String permission, String subject, String template, Map<String, String> variables);
}
