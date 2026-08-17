package io.datacatalog.embedding;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Real sentence embeddings from a local MiniLM model (all-MiniLM-L6-v2) running in-process
 * via ONNX Runtime — no API key, no network call at inference time. Selected with
 * {@code app.embedding.provider=minilm}; the deterministic fake stays the default.
 *
 * <p>The model files (~86 MB) are fetched, never committed: construction fails fast with
 * the fetch instruction when they are missing, so a misconfigured deployment dies at
 * startup instead of on the first write.
 */
public class OnnxMiniLmEmbeddingClient implements EmbeddingClient, AutoCloseable {

    private static final int DIMENSIONS = 384;

    public OnnxMiniLmEmbeddingClient(Path modelPath, Path tokenizerPath) {
        requireFile(modelPath);
        requireFile(tokenizerPath);
    }

    private static void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("MiniLM model file not found: " + path
                    + " — run scripts/fetch-minilm.sh to download it" + " (model files are not committed)");
        }
    }

    @Override
    public float[] embed(String text) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public void close() {}
}
