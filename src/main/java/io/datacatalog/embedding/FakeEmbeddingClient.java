package io.datacatalog.embedding;

import java.util.Locale;

/**
 * Deterministic, similarity-preserving embedder for tests and local dev: no model, no
 * network, no key. Tokens are hashed onto dimensions so texts sharing words land closer
 * than unrelated texts — which keeps ranking assertions meaningful.
 */
public class FakeEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSIONS = 384;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.isEmpty()) {
                continue;
            }
            // Feature hashing: each token contributes ±1 to one dimension. The sign bit is
            // taken from a different part of the hash than the bucket, so collisions on the
            // bucket don't imply agreement on the sign.
            int hash = token.hashCode();
            int bucket = Math.floorMod(hash, DIMENSIONS);
            float sign = ((hash >>> 16) & 1) == 0 ? 1.0f : -1.0f;
            vector[bucket] += sign;
        }
        double norm = 0;
        for (float component : vector) {
            norm += component * component;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] /= (float) norm;
            }
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
