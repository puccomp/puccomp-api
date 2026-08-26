package br.com.puccomp.api.recruitment;

import br.com.puccomp.api.email.EmailService;
import br.com.puccomp.api.recruitment.candidacies.CandidacyReceiptResponse;
import br.com.puccomp.api.recruitment.candidacies.CandidacyStatus;
import br.com.puccomp.api.recruitment.candidacies.SubmitCandidacyRequest;
import br.com.puccomp.api.recruitment.candidates.CandidateRequest;
import br.com.puccomp.api.recruitment.candidates.CandidateResponse;
import br.com.puccomp.api.recruitment.processes.ChangeStatusRequest;
import br.com.puccomp.api.recruitment.processes.PublicProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessRequest;
import br.com.puccomp.api.recruitment.processes.SelectionProcessResponse;
import br.com.puccomp.api.recruitment.processes.SelectionProcessStatus;
import br.com.puccomp.api.shared.exception.ErrorResponse;
import br.com.puccomp.api.shared.reference.Standing;
import br.com.puccomp.api.support.AbstractIntegrationTest;
import br.com.puccomp.api.support.TestSeeder;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestSeeder.class)
class CandidacyIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private TestSeeder seeder;

        @MockitoBean
        private EmailService emailService; // Mocka o serviço de e-mail inteiro

        @BeforeEach
        void setupEmailMock() {
                // Garante que chamar o envio de e-mail não faça nada e não lance erros
                Mockito.doNothing().when(emailService).enviarConfirmacaoInscricao(Mockito.anyString(), Mockito.anyString());
        }
        

        @Test
        @DisplayName("candidato anônimo deve ver o processo aberto e se inscrever pelo slug da EJ")
        void shouldBrowseAndSubmitThroughPublicSurface() {
                String token = ownerOf("EJ Alpha", "ej-alpha-cand", "dono@alpha-cand.dev");
                UUID processId = openProcess(token, "Processo Alpha", "Edital A");

                ResponseEntity<List<PublicProcessResponse>> open = get("/v1/public/ej-alpha-cand/processes", null,
                                new ParameterizedTypeReference<List<PublicProcessResponse>>() {
                                });
                assertThat(open.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(open.getBody()).singleElement()
                                .satisfies(p -> assertThat(p.title()).isEqualTo("Processo Alpha"));

                ResponseEntity<PublicProcessResponse> detail = get(publicProcess("ej-alpha-cand", processId), null,
                                PublicProcessResponse.class);
                assertThat(detail.getBody().description()).isEqualTo("Edital A");

                ResponseEntity<CandidacyReceiptResponse> submit = post(publicProcess("ej-alpha-cand", processId)
                                + "/candidacies", candidacy("joao@example.com"), null, CandidacyReceiptResponse.class);
                assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(submit.getBody().status()).isEqualTo(CandidacyStatus.SUBMITTED);

                assertThat(getWithToken("/v1/recruitment/processes/" + processId + "/candidacies", token).getBody())
                                .contains("joao@example.com");
        }

        @Test
        @DisplayName("candidato que já existe é reaproveitado na inscrição, com os dados de contato atualizados")
        void shouldReuseExistingCandidateOnSubmit() {
                String token = ownerOf("EJ Candidato Existente", "ej-candidato-existente",
                                "dono@candidato-existente.dev");
                String email = "candidato.existente@email.com";
                post("/v1/recruitment/candidates",
                                new CandidateRequest("Candidato Existente", email, "+55 31 98888-7777",
                                                List.of("https://linkedin.com/in/existente")),
                                token, CandidateResponse.class);

                UUID processId = openProcess(token, "Processo Candidato Existente", null);

                ResponseEntity<CandidacyReceiptResponse> submit = post(
                                publicProcess("ej-candidato-existente", processId) + "/candidacies",
                                new SubmitCandidacyRequest("Candidato Existente", email, "+55 31 97777-6666",
                                                "Ciência da Computação", "4º período", null, true),
                                null, CandidacyReceiptResponse.class);

                assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(getWithToken("/v1/recruitment/processes/" + processId + "/candidacies", token).getBody())
                                .contains(email)
                                .contains("+55 31 97777-6666");
        }

        @Test
        @DisplayName("a mesma pessoa se inscreve em dois processos sem virar dois candidatos")
        void shouldKeepOneCandidateAcrossProcesses() {
                String token = ownerOf("EJ Historico", "ej-historico-cand", "dono@historico-cand.dev");
                String email = "recorrente@email.com";
                UUID primeiro = openProcess(token, "PS 2026.1", null);
                UUID segundo = openProcess(token, "PS 2026.2", null);

                assertThat(post(publicProcess("ej-historico-cand", primeiro) + "/candidacies",
                                candidacy(email), null, CandidacyReceiptResponse.class).getStatusCode())
                                .isEqualTo(HttpStatus.CREATED);
                assertThat(post(publicProcess("ej-historico-cand", segundo) + "/candidacies",
                                candidacy(email), null, CandidacyReceiptResponse.class).getStatusCode())
                                .isEqualTo(HttpStatus.CREATED);

                // se as duas inscrições tivessem criado candidatos separados, o cadastro abaixo passaria
                ResponseEntity<ErrorResponse> duplicado = post("/v1/recruitment/candidates",
                                new CandidateRequest("Recorrente", email, "+55 31 90000-0000", null),
                                token, ErrorResponse.class);
                assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("cadastro de candidato exige permissão de escrita em recrutamento")
        void shouldRejectCandidateWriteWithoutPermission() {
                UUID tenantId = seeder.seedTenant("EJ Sem Permissao", "ej-sem-permissao-cand");
                seeder.seedAccount(tenantId, "membro@sem-permissao.dev", "senha123", Standing.MEMBER);
                String membro = login("membro@sem-permissao.dev", "senha123");

                ResponseEntity<ErrorResponse> res = post("/v1/recruitment/candidates",
                                new CandidateRequest("Alguem", "alguem@email.com", "+55 31 90000-0000", null),
                                membro, ErrorResponse.class);

                assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("processo em DRAFT deve ser indistinguível de inexistente na superfície pública")
        void shouldHideDraftProcessesFromPublicSurface() {
                String token = ownerOf("EJ Rascunho", "ej-rascunho", "dono@rascunho.dev");
                UUID processId = createProcess(token, "Processo ainda não publicado");

                assertThat(get("/v1/public/ej-rascunho/processes", null,
                                new ParameterizedTypeReference<List<PublicProcessResponse>>() {
                                }).getBody()).isEmpty();

                assertThat(get(publicProcess("ej-rascunho", processId), null, ErrorResponse.class)
                                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

                assertThat(post(publicProcess("ej-rascunho", processId) + "/candidacies",
                                candidacy("cedo@example.com"), null, ErrorResponse.class)
                                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("slug de EJ inexistente deve devolver 404 sem vazar nada")
        void shouldRejectUnknownSlug() {
                ResponseEntity<ErrorResponse> response = get("/v1/public/ej-que-nao-existe/processes", null,
                                ErrorResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody().message()).isEqualTo("Empresa júnior não encontrada");
        }

        @Test
        @DisplayName("o slug da URL deve mandar sobre o tenant de quem está autenticado")
        void shouldBindCandidacyToSlugTenantNotToCallerTenant() {
                String tokenA = ownerOf("EJ Alpha", "ej-alpha-bind", "dono@alpha-bind.dev");
                String tokenB = ownerOf("EJ Beta", "ej-beta-bind", "dono@beta-bind.dev");
                UUID processA = openProcess(tokenA, "Processo Alpha", null);

                // Membro autenticado da EJ B usando a superfície pública da EJ A.
                assertThat(post(publicProcess("ej-alpha-bind", processA) + "/candidacies",
                                candidacy("espiao@example.com"), tokenB, CandidacyReceiptResponse.class)
                                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

                assertThat(getWithToken("/v1/recruitment/processes/" + processA + "/candidacies", tokenA).getBody())
                                .contains("espiao@example.com");
                assertThat(getWithToken("/v1/recruitment/processes/" + processA + "/candidacies", tokenB)
                                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("não deve aceitar inscrição duplicada para o mesmo email, ignorando maiúsculas")
        void shouldRejectDuplicateCandidacyIgnoringCase() {
                String token = ownerOf("EJ Duplicada", "ej-duplicada", "dono@duplicada.dev");
                String path = publicProcess("ej-duplicada", openProcess(token, "Processo", null)) + "/candidacies";

                assertThat(post(path, candidacy("maria@example.com"), null, CandidacyReceiptResponse.class)
                                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

                ResponseEntity<ErrorResponse> duplicate = post(path, candidacy("MARIA@example.com"), null,
                                ErrorResponse.class);
                assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(duplicate.getBody().message()).contains("Você já se inscreveu");
        }

        @Test
        @DisplayName("não deve aceitar inscrição em processo que saiu de OPEN")
        void shouldRejectCandidacyWhenProcessLeavesOpen() {
                String token = ownerOf("EJ Fechada", "ej-fechada", "dono@fechada.dev");
                UUID processId = openProcess(token, "Processo", null);

                patch("/v1/recruitment/processes/" + processId + "/status",
                                new ChangeStatusRequest(SelectionProcessStatus.CLOSED), token,
                                SelectionProcessResponse.class);

                ResponseEntity<ErrorResponse> submit = post(publicProcess("ej-fechada", processId) + "/candidacies",
                                candidacy("tarde@example.com"), null, ErrorResponse.class);
                assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(submit.getBody().message()).contains("não está aceitando inscrições");
        }

        @Test
        @DisplayName("não deve aceitar inscrição depois do prazo, mesmo com o processo OPEN")
        void shouldRejectCandidacyAfterDeadline() {
                String token = ownerOf("EJ Prazo", "ej-prazo", "dono@prazo.dev");

                Instant past = Instant.now().minusSeconds(7_200);
                UUID processId = post("/v1/recruitment/processes",
                                new SelectionProcessRequest("Processo", null, past, past.plusSeconds(3_600)),
                                token, SelectionProcessResponse.class).getBody().id();
                patch("/v1/recruitment/processes/" + processId + "/status",
                                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token,
                                SelectionProcessResponse.class);

                ResponseEntity<ErrorResponse> submit = post(publicProcess("ej-prazo", processId) + "/candidacies",
                                candidacy("atrasado@example.com"), null, ErrorResponse.class);

                assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(submit.getBody().message()).contains("fora do prazo");
        }

        @Test
        @DisplayName("a ficha do candidato guarda os links na ordem enviada, até o limite de 5")
        void shouldStoreLinksInOrder() {
                String token = ownerOf("EJ Links", "ej-links", "dono@links.dev");
                List<String> links = List.of("https://linkedin.com/in/ana", "https://github.com/ana",
                                "https://behance.net/ana", "https://ana.dev", "https://ana.dev/cv.pdf");

                ResponseEntity<CandidateResponse> criado = post("/v1/recruitment/candidates",
                                new CandidateRequest("Ana Lima", "ana.links@email.com", "+55 31 90000-0000", links),
                                token, CandidateResponse.class);

                assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(criado.getBody().links()).containsExactlyElementsOf(links);
        }

        @Test
        @DisplayName("mais de 5 links ou link que não é URL devem ser recusados")
        void shouldRejectInvalidLinks() {
                String token = ownerOf("EJ Links Invalidos", "ej-links-invalidos", "dono@links-inv.dev");

                ResponseEntity<ErrorResponse> demais = post("/v1/recruitment/candidates",
                                new CandidateRequest("Excesso", "excesso@email.com", "+55 31 90000-0000",
                                                List.of("https://a.dev", "https://b.dev", "https://c.dev",
                                                                "https://d.dev", "https://e.dev", "https://f.dev")),
                                token, ErrorResponse.class);
                assertThat(demais.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                ResponseEntity<ErrorResponse> naoUrl = post("/v1/recruitment/candidates",
                                new CandidateRequest("Invalido", "invalido@email.com", "+55 31 90000-0000",
                                                List.of("isso nao e uma url")),
                                token, ErrorResponse.class);
                assertThat(naoUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("inscrição sem links é aceita e não apaga os links que a ficha já tinha")
        void shouldKeepExistingLinksWhenResubmittingWithout() {
                String token = ownerOf("EJ Links Preservados", "ej-links-preservados", "dono@links-pres.dev");
                String email = "preserva@email.com";
                post("/v1/recruitment/candidates",
                                new CandidateRequest("Preserva", email, "+55 31 90000-0000",
                                                List.of("https://github.com/preserva")),
                                token, CandidateResponse.class);

                UUID processId = openProcess(token, "PS Links", null);
                ResponseEntity<CandidacyReceiptResponse> submit = post(
                                publicProcess("ej-links-preservados", processId) + "/candidacies",
                                new SubmitCandidacyRequest("Preserva", email, "+55 31 90000-0000",
                                                "Ciência da Computação", null, null, true),
                                null, CandidacyReceiptResponse.class);

                assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(getWithToken("/v1/recruitment/processes/" + processId + "/candidacies", token).getBody())
                                .contains("https://github.com/preserva");
        }

        @Test
        @DisplayName("período é texto livre: aceita 'formando' e aceita ausência")
        void shouldAcceptFreeFormCurrentTerm() {
                String token = ownerOf("EJ Periodo", "ej-periodo", "dono@periodo.dev");
                UUID processId = openProcess(token, "PS Periodo", null);

                assertThat(post(publicProcess("ej-periodo", processId) + "/candidacies",
                                new SubmitCandidacyRequest("Formando", "formando@email.com", "+55 31 90000-0000",
                                                "Engenharia de Software", "formando", null, true),
                                null, CandidacyReceiptResponse.class).getStatusCode())
                                .isEqualTo(HttpStatus.CREATED);

                assertThat(post(publicProcess("ej-periodo", processId) + "/candidacies",
                                new SubmitCandidacyRequest("Sem Periodo", "sem.periodo@email.com",
                                                "+55 31 90000-0000", "Engenharia de Software", null, null, true),
                                null, CandidacyReceiptResponse.class).getStatusCode())
                                .isEqualTo(HttpStatus.CREATED);

                assertThat(getWithToken("/v1/recruitment/processes/" + processId + "/candidacies", token).getBody())
                                .contains("formando");
        }

        private String ownerOf(String ejName, String slug, String email) {
                UUID tenantId = seeder.seedTenant(ejName, slug);
                seeder.seedAccount(tenantId, email, "senha123", Standing.OWNER);
                return login(email, "senha123");
        }

        private UUID createProcess(String token, String title) {
                return post("/v1/recruitment/processes", new SelectionProcessRequest(title, null, null, null),
                                token, SelectionProcessResponse.class).getBody().id();
        }

        private UUID openProcess(String token, String title, String description) {
                UUID processId = post("/v1/recruitment/processes",
                                new SelectionProcessRequest(title, description, null, null), token,
                                SelectionProcessResponse.class).getBody().id();

                patch("/v1/recruitment/processes/" + processId + "/status",
                                new ChangeStatusRequest(SelectionProcessStatus.OPEN), token,
                                SelectionProcessResponse.class);
                return processId;
        }

        private static String publicProcess(String ejSlug, UUID processId) {
                return "/v1/public/" + ejSlug + "/processes/" + processId;
        }

        private static SubmitCandidacyRequest candidacy(String email) {
                return new SubmitCandidacyRequest("João Silva", email, "+55 31 99999-8888",
                                "Sistemas de Informação", "3º período", null, true);
        }
}
