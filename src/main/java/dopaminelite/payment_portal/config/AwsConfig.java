package dopaminelite.payment_portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS configuration for S3 client.
 * Supports both explicit credentials and default credential provider chain.
 */
@Configuration
public class AwsConfig {

    @Value("${aws.access.key:}")
    private String accessKey;

    @Value("${aws.secret.key:}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        if (accessKey == null || accessKey.isEmpty()) {
            // Use default credentials provider (IAM role, environment variables, etc.)
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        }
        
        // Use explicit credentials
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
    }
}
