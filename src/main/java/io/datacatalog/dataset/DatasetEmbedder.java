package io.datacatalog.dataset;

import io.datacatalog.embedding.EmbeddingClient;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * The one place a dataset's embedding is computed: assembles the embedded text and fills
 * the vector. Shared by the synchronous write path ({@link DatasetService}) and the
 * embeddings backfill; the future event-driven consumer (roadmap slice B) plugs in here
 * too, so "what a dataset embeds from" cannot drift between call sites.
 */
@Component
public class DatasetEmbedder {

    private final EmbeddingClient embeddings;

    public DatasetEmbedder(EmbeddingClient embeddings) {
        this.embeddings = embeddings;
    }

    /** Recompute the dataset's embedding from its current state. */
    public void embed(Dataset dataset) {
        dataset.setEmbedding(embeddings.embed(embeddingText(dataset)));
    }

    /**
     * The text a dataset is embedded from: name + description + tags — the human-meaningful
     * fields, matching what keyword search covers. Team and metadata stay out (keys and
     * punctuation add noise, not meaning).
     */
    private static String embeddingText(Dataset d) {
        StringJoiner text = new StringJoiner(" ");
        text.add(d.getName());
        if (d.getDescription() != null) {
            text.add(d.getDescription());
        }
        d.getTags().forEach(text::add);
        return text.toString();
    }
}
