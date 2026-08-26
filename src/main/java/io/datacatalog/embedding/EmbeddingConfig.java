package io.datacatalog.embedding;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmbeddingConfig {

    /**
     * The deterministic fake is the default provider so the whole stack builds, tests, and
     * demos with nothing external. {@code matchIfMissing} keeps it the default when the
     * property is absent entirely (context slices that don't load application.yml).
     */
    @Bean
    @ConditionalOnProperty(name = "app.embedding.provider", havingValue = "fake", matchIfMissing = true)
    EmbeddingClient fakeEmbeddingClient() {
        return new FakeEmbeddingClient();
    }

    /**
     * Real sentence embeddings, opt-in via {@code app.embedding.provider=minilm}. Both
     * providers produce 384-dim vectors, matching the {@code vector(384)} column — swapping
     * providers changes vector quality, not the schema.
     */
    @Bean
    @ConditionalOnProperty(name = "app.embedding.provider", havingValue = "minilm")
    EmbeddingClient miniLmEmbeddingClient(
            @Value("${app.embedding.minilm.model-path}") String modelPath,
            @Value("${app.embedding.minilm.tokenizer-path}") String tokenizerPath) {
        return new OnnxMiniLmEmbeddingClient(Path.of(modelPath), Path.of(tokenizerPath));
    }
}
