package io.datacatalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class S3TestcontainersConfiguration {

    @Bean(destroyMethod = "stop")
    LocalStackContainer localStackContainer() {
        LocalStackContainer container = new LocalStackContainer(
                DockerImageName.parse("localstack/localstack:3"))
                .withServices(LocalStackContainer.Service.S3);
        container.start();
        return container;
    }

    @Bean
    DynamicPropertyRegistrar s3Properties(LocalStackContainer localStack) {
        return registry -> {
            registry.add("app.s3.endpoint", () -> localStack.getEndpoint().toString());
            registry.add("app.s3.region", localStack::getRegion);
            registry.add("app.s3.access-key", localStack::getAccessKey);
            registry.add("app.s3.secret-key", localStack::getSecretKey);
            registry.add("app.s3.bucket", () -> "datacatalog-test");
        };
    }
}
