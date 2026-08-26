package io.datacatalog.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Real sentence embeddings from a local MiniLM model (all-MiniLM-L6-v2) running in-process
 * via ONNX Runtime — no API key, no network call at inference time. Selected with
 * {@code app.embedding.provider=minilm}; the deterministic fake stays the default.
 *
 * <p>The ONNX export is the transformer backbone only, so this class applies the two
 * sentence-transformers steps the export leaves out: attention-mask-weighted mean pooling
 * over the token vectors, then L2 normalization. Unit-length output means the dot product
 * of two embeddings <em>is</em> their cosine similarity, matching the {@code <=>} cosine
 * distance the similarity query orders by.
 *
 * <p>The model files (~86 MB) are fetched, never committed: construction fails fast with
 * the fetch instruction when they are missing, so a misconfigured deployment dies at
 * startup instead of on the first write.
 */
public class OnnxMiniLmEmbeddingClient implements EmbeddingClient, AutoCloseable {

    private static final int DIMENSIONS = 384;

    /** all-MiniLM-L6-v2 was trained on 256-token inputs; longer text is truncated. */
    private static final int MAX_TOKENS = 256;

    /** Some MiniLM exports omit token_type_ids; anything outside this set is not our model. */
    private static final Set<String> SUPPORTED_INPUTS = Set.of("input_ids", "attention_mask", "token_type_ids");

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public OnnxMiniLmEmbeddingClient(Path modelPath, Path tokenizerPath) {
        requireFile(modelPath);
        requireFile(tokenizerPath);
        try {
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optTruncation(true)
                    .optMaxLength(MAX_TOKENS)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load MiniLM tokenizer from " + tokenizerPath, e);
        }
        // The tokenizer holds a native handle; if anything after this point fails, the
        // half-constructed object is never registered with Spring, so close() would never
        // run — clean up here before rethrowing.
        OrtSession session = null;
        try {
            this.environment = OrtEnvironment.getEnvironment();
            session = environment.createSession(modelPath.toString(), new OrtSession.SessionOptions());
            requireSupportedInputs(session.getInputNames());
            this.session = session;
        } catch (OrtException | RuntimeException e) {
            closeQuietly(session);
            tokenizer.close();
            throw e instanceof OrtException
                    ? new IllegalStateException("Failed to load MiniLM ONNX model from " + modelPath, e)
                    : (RuntimeException) e;
        }
    }

    private static void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("MiniLM model file not found: " + path
                    + " — run scripts/fetch-minilm.sh to download it" + " (model files are not committed)");
        }
    }

    /**
     * An incompatible-but-present export (different input names) would otherwise only fail
     * on the first embed() call — validating here keeps the "dies at startup, not on the
     * first write" guarantee for malformed files, not just missing ones.
     */
    private static void requireSupportedInputs(Set<String> inputNames) {
        if (!inputNames.contains("input_ids") || !inputNames.contains("attention_mask")) {
            throw new IllegalStateException(
                    "Unsupported MiniLM ONNX export: expected inputs input_ids and attention_mask, got " + inputNames);
        }
        for (String name : inputNames) {
            if (!SUPPORTED_INPUTS.contains(name)) {
                throw new IllegalStateException("Unsupported MiniLM ONNX export: unexpected input '" + name
                        + "' (supported: " + SUPPORTED_INPUTS + ")");
            }
        }
    }

    private static void closeQuietly(OrtSession session) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (OrtException suppressed) {
            // cleanup on a failure path — the original exception is the one that matters
        }
    }

    @Override
    public float[] embed(String text) {
        Encoding encoding = tokenizer.encode(text);
        long[] attentionMask = encoding.getAttentionMask();
        try (OnnxTensor ids = tensorOf(encoding.getIds());
                OnnxTensor mask = tensorOf(attentionMask);
                OnnxTensor types = tensorOf(encoding.getTypeIds())) {
            // Feed exactly the inputs this export declares; some MiniLM exports omit
            // token_type_ids, so the map is built from the model's own input list.
            Map<String, OnnxTensor> inputs = new HashMap<>();
            for (String name : session.getInputNames()) {
                switch (name) {
                    case "input_ids" -> inputs.put(name, ids);
                    case "attention_mask" -> inputs.put(name, mask);
                    case "token_type_ids" -> inputs.put(name, types);
                    default -> throw new IllegalStateException("Unexpected MiniLM model input: " + name);
                }
            }
            try (OrtSession.Result result = session.run(inputs)) {
                // last_hidden_state: [batch=1][tokens][DIMENSIONS]
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                return meanPoolAndNormalize(hidden[0], attentionMask);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("MiniLM inference failed", e);
        }
    }

    private OnnxTensor tensorOf(long[] values) throws OrtException {
        return OnnxTensor.createTensor(environment, new long[][] {values});
    }

    private static float[] meanPoolAndNormalize(float[][] tokenVectors, long[] attentionMask) {
        float[] pooled = new float[DIMENSIONS];
        int realTokens = 0;
        for (int token = 0; token < tokenVectors.length; token++) {
            if (attentionMask[token] == 0) {
                continue; // padding contributes nothing to the sentence meaning
            }
            for (int d = 0; d < DIMENSIONS; d++) {
                pooled[d] += tokenVectors[token][d];
            }
            realTokens++;
        }
        if (realTokens > 0) {
            for (int d = 0; d < DIMENSIONS; d++) {
                pooled[d] /= realTokens;
            }
        }
        double norm = 0;
        for (float component : pooled) {
            norm += component * component;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int d = 0; d < DIMENSIONS; d++) {
                pooled[d] /= (float) norm;
            }
        }
        return pooled;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public void close() {
        // The OrtEnvironment is a process-wide singleton and is deliberately not closed.
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to close the MiniLM ONNX session", e);
        } finally {
            tokenizer.close();
        }
    }
}
