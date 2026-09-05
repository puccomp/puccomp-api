package br.com.puccomp.api.files.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
class FileConfig {

    @Bean
    @ConditionalOnProperty(prefix = "puccomp.files", name = "enabled", havingValue = "true")
    ObjectStorage objectStorage(FileProperties properties) {
        var service = S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build();
        var client = S3Client.builder().region(Region.of(properties.region())).serviceConfiguration(service)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)));
        var signer = S3Presigner.builder().region(Region.of(properties.region())).serviceConfiguration(service);
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            URI endpoint = URI.create(properties.endpoint());
            client.endpointOverride(endpoint);
            signer.endpointOverride(endpoint);
        }
        return new S3ObjectStorage(client.build(), signer.build());
    }
}
