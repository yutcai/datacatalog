package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * GET /v1/datasets/search/semantic?q=&k= — meaning-based ranking over the whole catalog,
 * returned as a scored top-k list, not a page.
 *
 * <p>Probe tokens (fjord / yurt / borscht) are unique to this class and pre-verified to hash
 * into three distinct fake-embedder buckets, so sharing 3, 2 or 1 of the query's tokens
 * yields strictly decreasing similarity. The database is shared across test classes, so
 * ranking assertions filter to the rows this test created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class DatasetSemanticSearchTest {

    // The endpoint's k cap; querying with it proves absence means "ranked out", not "cut off".
    private static final int MAX_K = 100;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void ranksByMeaningAndReportsDescendingScores() {
        String token = authedUser("semantic-rank");
        String sharesAll = create(token, "fjord yurt borscht");
        String sharesTwo = create(token, "fjord yurt");
        String sharesOne = create(token, "fjord");
        Set<String> mine = Set.of(sharesAll, sharesTwo, sharesOne);

        List<Map<String, Object>> hits = items(search("?q=fjord yurt borscht&k=" + MAX_K, token)).stream()
                .filter(item -> mine.contains(item.get("id")))
                .toList();

        assertThat(hits).extracting(item -> item.get("id")).containsExactly(sharesAll, sharesTwo, sharesOne);
        List<Double> scores = hits.stream()
                .map(item -> ((Number) item.get("score")).doubleValue())
                .toList();
        // Score is cosine similarity (1 − distance): identical text scores ~1, and every
        // dropped token strictly lowers it.
        assertThat(scores.get(0)).isCloseTo(1.0, within(1e-6));
        assertThat(scores.get(1)).isGreaterThan(scores.get(2));
    }

    @Test
    void returnsARankedListNotAPage() {
        String token = authedUser("semantic-shape");
        create(token, "quinoa saffron");

        Map<String, Object> body = search("?q=quinoa&k=5", token);

        // The top-k contract: items only — no page / limit / total envelope.
        assertThat(body).containsOnlyKeys("items");
    }

    @Test
    void kCapsTheNumberOfResults() {
        String token = authedUser("semantic-k");
        create(token, "walrus tundra");
        create(token, "walrus");
        create(token, "tundra");

        assertThat(items(search("?q=walrus tundra&k=2", token))).hasSize(2);
    }

    @Test
    void kBelowOneIsClampedNotAnError() {
        String token = authedUser("semantic-clamp");
        create(token, "puffin geyser");

        // Same silent-clamp contract as the keyword search's limit: k=0 means "top 1", not 400.
        assertThat(items(search("?q=puffin&k=0", token))).hasSize(1);
    }

    @Test
    void missingOrBlankQIsBadRequest() {
        String token = authedUser("semantic-badreq");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> missing =
                rest.exchange("/v1/datasets/search/semantic", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        ResponseEntity<Map> blank = rest.exchange(
                "/v1/datasets/search/semantic?q=  ", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void withoutTokenIsUnauthorized() {
        ResponseEntity<Map> resp = rest.exchange(
                "/v1/datasets/search/semantic?q=anything",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private Map<String, Object> search(String query, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.exchange(
                "/v1/datasets/search/semantic" + query, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private List<Map<String, Object>> items(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("items");
    }

    private String create(String token, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp =
                rest.postForEntity("/v1/datasets", new HttpEntity<>(Map.of("name", name), headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private String authedUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register", Map.of("username", username, "password", "pw-12345"), Map.class);
        ResponseEntity<Map> token =
                rest.postForEntity("/v1/auth/token", Map.of("username", username, "password", "pw-12345"), Map.class);
        return (String) token.getBody().get("accessToken");
    }
}
