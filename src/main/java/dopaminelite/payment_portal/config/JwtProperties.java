package dopaminelite.payment_portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret = "your-256-bit-secret-key-change-this-in-production-must-be-at-least-256-bits-long";
    private long expiration = 86400000; // 24 hours in milliseconds
}
