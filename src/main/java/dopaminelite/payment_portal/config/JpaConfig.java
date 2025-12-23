package dopaminelite.payment_portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration class enabling automatic auditing for entity timestamps and user tracking.
 * When JPA Auditing is enabled, entities extending AuditableEntity will have their
 * createdAt, updatedAt, createdBy, and updatedBy fields automatically populated.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
