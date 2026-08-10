package br.com.puccomp.api.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate rest;

    protected String login(String email, String password) {
        ResponseEntity<Map<String, Object>> res = rest.exchange("/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("access_token");
    }

    protected ResponseEntity<String> getWithToken(String path, String bearerToken) {
        return get(path, bearerToken, String.class);
    }

    protected <T> ResponseEntity<T> get(String path, String bearerToken, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(bearerToken)), responseType);
    }

    protected <T> ResponseEntity<T> get(String path, String bearerToken, ParameterizedTypeReference<T> responseType) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(bearerToken)), responseType);
    }

    protected <T> ResponseEntity<T> post(String path, Object body, String bearerToken, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(bearerToken)), responseType);
    }

    protected <T> ResponseEntity<T> patch(String path, Object body, String bearerToken, Class<T> responseType) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers(bearerToken)), responseType);
    }

    /** {@code bearerToken} nulo monta uma requisição anônima — usado nos endpoints públicos. */
    private static HttpHeaders headers(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null)
            headers.setBearerAuth(bearerToken);
        return headers;
    }
}
