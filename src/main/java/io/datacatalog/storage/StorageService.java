package io.datacatalog.storage;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Object storage via S3. The app never streams file bytes — it only issues short-lived
 * pre-signed URLs the client uses to transfer directly to/from S3, and verifies objects
 * server-side with HEAD.
 */
@Service
public class StorageService {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public StorageService(S3Client s3, S3Presigner presigner, @Value("${app.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public URL presignPut(String key) {
        return presigner.presignPutObject(b -> b
                .signatureDuration(UPLOAD_TTL)
                .putObjectRequest(r -> r.bucket(bucket).key(key))).url();
    }

    public URL presignGet(String key) {
        return presigner.presignGetObject(b -> b
                .signatureDuration(DOWNLOAD_TTL)
                .getObjectRequest(r -> r.bucket(bucket).key(key))).url();
    }

    /** HEAD the object; empty if it isn't there (the upload never happened). */
    public Optional<HeadObjectResponse> head(String key) {
        try {
            return Optional.of(s3.headObject(b -> b.bucket(bucket).key(key)));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Create the bucket if missing — used for local dev / LocalStack, not real AWS. */
    public void ensureBucketExists() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (S3Exception e) {
            s3.createBucket(b -> b.bucket(bucket));
        }
    }
}
