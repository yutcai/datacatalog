package io.datacatalog.config;

import io.datacatalog.storage.StorageService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 wiring. When {@code app.s3.endpoint} is set (LocalStack / local dev) we override the
 * endpoint, force path-style access, and use static credentials. When it's blank (real AWS)
 * the SDK uses the default region/credentials chain.
 *
 * <p>The presigner can use a separate {@code app.s3.public-endpoint}: the URL the client
 * receives must be reachable from the client, which may differ from the address the app uses
 * to reach S3 (e.g. inside docker the app talks to {@code localstack:4566} but the browser
 * on the host needs {@code localhost:4566}).
 */
@Configuration
public class S3Config {

    @Bean
    S3Client s3Client(
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.endpoint:}") String endpoint,
            @Value("${app.s3.access-key:}") String accessKey,
            @Value("${app.s3.secret-key:}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(staticCredentials(accessKey, secretKey));
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(
            @Value("${app.s3.region}") String region,
            @Value("${app.s3.endpoint:}") String endpoint,
            @Value("${app.s3.public-endpoint:}") String publicEndpoint,
            @Value("${app.s3.access-key:}") String accessKey,
            @Value("${app.s3.secret-key:}") String secretKey) {
        String presignEndpoint = StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint;
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        if (StringUtils.hasText(presignEndpoint)) {
            builder.endpointOverride(URI.create(presignEndpoint))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .credentialsProvider(staticCredentials(accessKey, secretKey));
        }
        return builder.build();
    }

    /** Create the bucket on startup for local dev only (endpoint override present). */
    @Bean
    ApplicationRunner ensureBucket(StorageService storage, @Value("${app.s3.endpoint:}") String endpoint) {
        return args -> {
            if (StringUtils.hasText(endpoint)) {
                storage.ensureBucketExists();
            }
        };
    }

    private static StaticCredentialsProvider staticCredentials(String accessKey, String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
