package io.datacatalog.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real inference against the actual MiniLM model. The model files are fetched, never
 * committed, so this whole class skips itself when they are absent (CI). Run
 * {@code scripts/fetch-minilm.sh} once to execute these locally.
 *
 * <p>The semantic assertions are the point of the real provider: "car" and "automobile"
 * share no tokens, so the fake's feature hashing cannot place them near each other — only
 * a model that has learned meaning can. That is precisely what this class pins down.
 */
class OnnxMiniLmEmbeddingClientTest {

    private static final Path MODEL = Path.of("models/all-MiniLM-L6-v2/model.onnx");
    private static final Path TOKENIZER = Path.of("models/all-MiniLM-L6-v2/tokenizer.json");

    private static OnnxMiniLmEmbeddingClient client;

    @BeforeAll
    static void loadModel() {
        assumeTrue(
                Files.isRegularFile(MODEL) && Files.isRegularFile(TOKENIZER),
                "MiniLM model not fetched — run scripts/fetch-minilm.sh");
        client = new OnnxMiniLmEmbeddingClient(MODEL, TOKENIZER);
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void producesUnitVectorsOfTheDeclaredDimension() {
        float[] vector = client.embed("quarterly sales revenue by region");

        assertThat(vector).hasSize(client.dimensions());
        assertThat(norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void isDeterministicForTheSameText() {
        assertThat(client.embed("weather station telemetry"))
                .containsExactly(client.embed("weather station telemetry"));
    }

    @Test
    void placesSynonymsCloserThanUnrelatedWords() {
        // No shared tokens anywhere: this ranking is impossible for the hashing fake.
        float[] car = client.embed("car");
        float[] automobile = client.embed("automobile");
        float[] banana = client.embed("banana");

        assertThat(cosine(car, automobile)).isGreaterThan(cosine(car, banana));
    }

    @Test
    void placesRelatedSentencesCloserThanUnrelatedOnes() {
        float[] query = client.embed("European revenue figures from last year");
        float[] sales = client.embed("Annual sales totals for stores in France and Germany");
        float[] offTopic = client.embed("Feeding schedule for the office aquarium");

        assertThat(cosine(query, sales)).isGreaterThan(cosine(query, offTopic));
    }

    @Test
    void blankTextStillProducesAFiniteVector() {
        float[] vector = client.embed("   ");

        assertThat(vector).hasSize(client.dimensions());
        for (float component : vector) {
            assertThat(Float.isFinite(component)).isTrue();
        }
    }

    @Test
    void truncatesVeryLongTextInsteadOfFailing() {
        float[] vector = client.embed("dataset ".repeat(5_000));

        assertThat(vector).hasSize(client.dimensions());
        assertThat(norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    private static double cosine(float[] a, float[] b) {
        // Vectors are L2-normalized, so the dot product is the cosine similarity.
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    private static double norm(float[] vector) {
        double sum = 0;
        for (float component : vector) {
            sum += component * component;
        }
        return Math.sqrt(sum);
    }
}
