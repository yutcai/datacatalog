package io.datacatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import io.datacatalog.dataset.Dataset;
import io.datacatalog.dataset.DatasetRepository;
import io.datacatalog.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /v1/datasets?q=&tag=&owner=&page=&limit= — search, filter, offset pagination.
 *
 * Tests share one Postgres (Spring context cache), so each test seeds under its own unique
 * owner and filters by {@code owner=} to isolate its rows from other tests' data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class DatasetSearchTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private DatasetRepository datasetRepo;
    @Autowired
    private UserRepository userRepo;

    @Test
    void filtersByOwnerUsername() {
        String token = authedUser("owner-filter");
        create(token, Map.of("name", "first"));
        create(token, Map.of("name", "second"));

        Map<String, Object> page = search("?owner=" + tokenUsername, token);

        assertThat(((Number) page.get("total")).intValue()).isEqualTo(2);
        assertThat(items(page)).extracting(m -> m.get("name"))
                .containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void qMatchesNameOrDescription() {
        String token = authedUser("q-search");
        String mark = "zq" + UUID.randomUUID().toString().replace("-", "");
        create(token, Map.of("name", "alpha-" + mark, "description", "plain"));   // hit via name
        create(token, Map.of("name", "beta", "description", "see " + mark + " inside")); // hit via description
        create(token, Map.of("name", "gamma", "description", "unrelated"));        // no hit

        Map<String, Object> page = search("?owner=" + tokenUsername + "&q=" + mark, token);

        assertThat(items(page)).extracting(m -> m.get("name"))
                .containsExactlyInAnyOrder("alpha-" + mark, "beta");
    }

    @Test
    void filtersByTag() {
        String token = authedUser("tag-search");
        String tag = "t" + UUID.randomUUID().toString().replace("-", "");
        create(token, Map.of("name", "tagged", "tags", List.of(tag, "common")));
        create(token, Map.of("name", "untagged", "tags", List.of("common")));

        Map<String, Object> page = search("?owner=" + tokenUsername + "&tag=" + tag, token);

        assertThat(items(page)).extracting(m -> m.get("name")).containsExactly("tagged");
    }

    @Test
    void paginatesAndReportsTotal() {
        String token = authedUser("paging");
        create(token, Map.of("name", "p1"));
        create(token, Map.of("name", "p2"));
        create(token, Map.of("name", "p3"));

        Map<String, Object> first = search("?owner=" + tokenUsername + "&page=0&limit=2", token);
        assertThat(((Number) first.get("total")).intValue()).isEqualTo(3);
        assertThat(((Number) first.get("page")).intValue()).isEqualTo(0);
        assertThat(((Number) first.get("limit")).intValue()).isEqualTo(2);
        assertThat(items(first)).hasSize(2);

        Map<String, Object> second = search("?owner=" + tokenUsername + "&page=1&limit=2", token);
        assertThat(items(second)).hasSize(1);
    }

    @Test
    void paginationIsStableWhenTimestampsCollide() {
        String token = authedUser("tiebreak");
        // Seed 5 rows in ONE transaction so they share an identical created_at — the only
        // case where ORDER BY created_at alone is ambiguous and a tiebreaker is required.
        seedSameTimestamp(tokenUsername, 5);

        List<String> ids = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            items(search("?owner=" + tokenUsername + "&page=" + page + "&limit=2", token))
                    .forEach(m -> ids.add((String) m.get("id")));
        }

        // Every row appears exactly once and in one deterministic order across page
        // boundaries — guaranteed only by the (created_at, id) tiebreaker.
        assertThat(ids).hasSize(5).doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void searchWithoutTokenIsUnauthorized() {
        ResponseEntity<Map> resp = rest.exchange("/v1/datasets", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

    private String tokenUsername;

    private Map<String, Object> search(String query, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.exchange("/v1/datasets" + query, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("items");
    }

    private void seedSameTimestamp(String owner, int count) {
        UUID ownerId = userRepo.findByUsername(owner).orElseThrow().getId();
        List<Dataset> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            batch.add(new Dataset("ts-" + i, ownerId, null, null, List.of(), Map.of()));
        }
        datasetRepo.saveAll(batch); // single transaction => one shared transaction_timestamp()
    }

    private void create(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.postForEntity("/v1/datasets",
                new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String authedUser(String prefix) {
        tokenUsername = prefix + "-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register",
                Map.of("username", tokenUsername, "password", "pw-12345"), Map.class);
        ResponseEntity<Map> token = rest.postForEntity("/v1/auth/token",
                Map.of("username", tokenUsername, "password", "pw-12345"), Map.class);
        return (String) token.getBody().get("accessToken");
    }
}
