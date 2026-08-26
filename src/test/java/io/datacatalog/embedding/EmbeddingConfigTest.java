package io.datacatalog.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Provider selection is plain bean wiring, so it is tested without a full Spring Boot
 * start: an {@link ApplicationContextRunner} with just {@link EmbeddingConfig} evaluates
 * the {@code @ConditionalOnProperty} logic exactly as the real context would.
 *
 * <p>Every test here runs in CI. Only the last one touches the actual model files, and it
 * skips itself when they are absent (they are fetched, never committed).
 */
class EmbeddingConfigTest {

    private static final Path MODEL = Path.of("models/all-MiniLM-L6-v2/model.onnx");
    private static final Path TOKENIZER = Path.of("models/all-MiniLM-L6-v2/tokenizer.json");

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EmbeddingConfig.class);

    @Test
    void fakeIsTheDefaultWhenNoProviderIsConfigured() {
        runner.run(
                context -> assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(FakeEmbeddingClient.class));
    }

    @Test
    void fakeCanBeSelectedExplicitly() {
        runner.withPropertyValues("app.embedding.provider=fake")
                .run(context ->
                        assertThat(context.getBean(EmbeddingClient.class)).isInstanceOf(FakeEmbeddingClient.class));
    }

    @Test
    void miniLmSelectionFailsFastWhenTheModelFilesAreMissing() {
        runner.withPropertyValues(
                        "app.embedding.provider=minilm",
                        "app.embedding.minilm.model-path=build/no-such-model.onnx",
                        "app.embedding.minilm.tokenizer-path=build/no-such-tokenizer.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("no-such-model.onnx")
                            .hasMessageContaining("scripts/fetch-minilm.sh");
                });
    }

    @Test
    void miniLmIsSelectedWhenConfigured() {
        assumeTrue(
                Files.isRegularFile(MODEL) && Files.isRegularFile(TOKENIZER),
                "MiniLM model not fetched — run scripts/fetch-minilm.sh");
        runner.withPropertyValues(
                        "app.embedding.provider=minilm",
                        "app.embedding.minilm.model-path=" + MODEL,
                        "app.embedding.minilm.tokenizer-path=" + TOKENIZER)
                .run(context -> assertThat(context.getBean(EmbeddingClient.class))
                        .isInstanceOf(OnnxMiniLmEmbeddingClient.class));
    }
}
