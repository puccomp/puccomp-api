package br.com.puccomp.api.identity.password;

import br.com.puccomp.api.identity.account.AuthPrincipal;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/v1/auth/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService service;

    @Operation(summary = "Pede o link de redefinição por e-mail. Responde 202 mesmo se o e-mail não "
            + "tiver conta, para não revelar quem é cadastrado")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @PostMapping("/forgot")
    public void forgot(@RequestBody @Valid ForgotPasswordRequest request) {
        service.forgot(request);
    }

    @Operation(summary = "Redefine a senha com o token do e-mail. O token é de uso único")
    @ApiResponse(responseCode = "400", description = "Link inválido, expirado ou já usado; ou senha fora da política",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @PostMapping("/reset")
    public void reset(@RequestBody @Valid ResetPasswordRequest request) {
        service.reset(request);
    }

    @Operation(summary = "Troca a senha de quem já está autenticado, confirmando a senha atual")
    @ApiResponse(responseCode = "400", description = "Senha atual incorreta, nova senha igual à atual, "
            + "ou senha fora da política",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/change")
    public void change(@AuthenticationPrincipal AuthPrincipal principal,
                       @RequestBody @Valid ChangePasswordRequest request) {
        service.change(principal, request);
    }
}
