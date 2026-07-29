package io.datacatalog.embedding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmbeddingConfig {

    /**
     * The deterministic fake is the default provider so the whole stack builds, tests, and
     * demos with nothing external. A real model-backed client will be an alternative bean
     * selected by configuration.
     */
    @Bean
    EmbeddingClient embeddingClient() {
        return new FakeEmbeddingClient();
    }
}
