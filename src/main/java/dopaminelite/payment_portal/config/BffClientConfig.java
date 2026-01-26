package dopaminelite.payment_portal.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration class for BFF (Backend for Frontend) client.
 * Provides RestTemplate bean configured for making HTTP calls to the BFF service.
 */
@Configuration
public class BffClientConfig {

    /**
     * Creates a configured RestTemplate bean for BFF service communication.
     * Includes connection and read timeouts to prevent hanging requests.
     *
     * @param builder RestTemplateBuilder provided by Spring Boot
     * @return configured RestTemplate instance
     */
    @Bean
    public RestTemplate bffRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
