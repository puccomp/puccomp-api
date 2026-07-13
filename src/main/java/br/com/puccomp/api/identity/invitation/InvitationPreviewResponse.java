package br.com.puccomp.api.identity.invitation;

import br.com.puccomp.api.organization.CourseCatalog;

import java.util.List;

public record InvitationPreviewResponse(String ejName, String email, List<CourseCatalog.CourseOption> courses) { }
