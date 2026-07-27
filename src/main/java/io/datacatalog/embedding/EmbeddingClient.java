package io.datacatalog.embedding;

/**
 * Turns text into a fixed-dimension vector for similarity search. Embeddings normally come
 * from a model — an external, slow, costly dependency — so generation sits behind this
 * interface (the same seam as {@code StorageService} for S3): the similarity machinery is
 * exercised against real pgvector with a deterministic fake, and the real provider is a
 * drop-in swap selected by configuration.
 */
public interface EmbeddingClient {

    /** Embed the given text into a vector of exactly {@link #dimensions()} components. */
    float[] embed(String text);

    /** The fixed dimension of every vector this client produces. */
    int dimensions();
}
