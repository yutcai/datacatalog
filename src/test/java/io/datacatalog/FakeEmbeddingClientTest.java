package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.datacatalog.embedding.EmbeddingClient;
import io.datacatalog.embedding.FakeEmbeddingClient;
import org.junit.jupiter.api.Test;

/**
 * The Fake must be deterministic AND similarity-preserving: texts that share words land
 * closer in vector space than unrelated texts. That is what makes downstream ranking
 * assertions meaningful — a naive whole-string hash would make "quarterly sales" as far
 * from "sales revenue" as from "childcare rota", and ranking tests would assert an
 * artifact of the hash, not meaning.
 */
class FakeEmbeddingClientTest {

    private final EmbeddingClient client = new FakeEmbeddingClient();

    @Test
    void embedsToTheDeclaredFixedDimension() {
        assertThat(client.dimensions()).isEqualTo(384);
        assertThat(client.embed("quarterly sales report")).hasSize(384);
    }

    @Test
    void sameTextAlwaysEmbedsToTheSameVector() {
        assertThat(client.embed("quarterly sales report")).isEqualTo(client.embed("quarterly sales report"));
    }

    @Test
    void vectorsAreL2Normalized() {
        float[] vector = client.embed("european revenue figures");

        assertThat(norm(vector)).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void textsSharingWordsAreCloserThanUnrelatedTexts() {
        float[] query = client.embed("quarterly sales report");
        float[] related = client.embed("annual sales revenue");
        float[] unrelated = client.embed("childcare rota schedule");

        assertThat(cosine(query, related)).isGreaterThan(cosine(query, unrelated));
    }

    @Test
    void tokenizationIgnoresCaseAndPunctuation() {
        assertThat(client.embed("Quarterly, SALES: report!")).isEqualTo(client.embed("quarterly sales report"));
    }

    @Test
    void blankTextEmbedsToTheZeroVector() {
        assertThat(client.embed("   ")).hasSize(384).containsOnly(0.0f);
    }

    /** Vectors are L2-normalized, so cosine similarity reduces to the dot product. */
    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    private static double norm(float[] vector) {
        double sumOfSquares = 0;
        for (float component : vector) {
            sumOfSquares += component * component;
        }
        return Math.sqrt(sumOfSquares);
    }
}
