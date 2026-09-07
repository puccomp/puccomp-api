package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.identity.tenant.OrganizationView;
import br.com.puccomp.api.organization.CourseCatalog;

import java.util.List;

/**
 * Prévia pública do convite. O {@code accountExists} desambigua o campo {@code password} do aceite:
 * {@code false} significa "defina uma senha", {@code true} significa "confirme a senha da conta que
 * você já tem" — sem ele o cliente só descobre qual dos dois tomando 401. Não abre enumeração de
 * contas: quem tem o token já recebeu o email e o {@code email} do convite volta aqui de qualquer jeito.
 */
public record InvitationPreviewResponse(OrganizationView organization, String email, boolean accountExists,
                                        List<CourseCatalog.CourseOption> courses) { }
