package dopaminelite.payment_portal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link PayHereProperties}.
 */
@Configuration
@EnableConfigurationProperties(PayHereProperties.class)
public class PayHereConfig {
}
