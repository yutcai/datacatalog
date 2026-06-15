package io.datacatalog;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registerThenTokenThenAccessProtectedEndpoint() {
        String username = "alice-" + UUID.randomUUID();
        register(username, "s3cret-pw");

        String token = obtainToken(username, "s3cret-pw");
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> me = rest.exchange("/v1/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("username", username);
    }

    @Test
    void passwordIsStoredHashedNotPlaintext() {
        String username = "bob-" + UUID.randomUUID();
        register(username, "plaintext-pw");

        String stored = jdbc.queryForObject(
                "select password_hash from users where username = ?", String.class, username);

        assertThat(stored).isNotEqualTo("plaintext-pw").startsWith("$2");
    }

    @Test
    void duplicateUsernameIsConflict() {
        String username = "carol-" + UUID.randomUUID();
        assertThat(register(username, "pw1").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register(username, "pw2").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void tokenWithWrongPasswordIsUnauthorized() {
        String username = "dave-" + UUID.randomUUID();
        register(username, "right-pw");

        ResponseEntity<Map> resp = rest.postForEntity(
                "/v1/auth/token", Map.of("username", username, "password", "wrong-pw"), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() {
        ResponseEntity<Map> resp = rest.getForEntity("/v1/me", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointWithGarbageTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-real-jwt");
        ResponseEntity<Map> resp = rest.exchange(
                "/v1/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> register(String username, String password) {
        return rest.postForEntity(
                "/v1/auth/register", Map.of("username", username, "password", password), Map.class);
    }

    private String obtainToken(String username, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/v1/auth/token", Map.of("username", username, "password", password), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }
}
