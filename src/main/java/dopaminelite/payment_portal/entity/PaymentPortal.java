package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Entity representing a payment portal for a specific month and year.
 * A payment portal is used to collect and manage payment submissions from students.
 * Each portal has a unique name and can be published or hidden based on visibility settings.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_portals", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name"})
})
public class PaymentPortal extends AuditableEntity {
    
    /**
     * Month for which this portal is collecting payments (1-12).
     */
    @Column(name = "portal_month", nullable = false)
    private Integer month;
    
    /**
     * Year for which this portal is collecting payments.
     */
    @Column(name = "portal_year", nullable = false)
    private Integer year;
    
    /**
     * Unique identifier name for the portal. Must be unique across all portals.
     */
    @Column(nullable = false, unique = true)
    private String name;
    
    /**
     * Human-readable display name shown to users.
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    /**
     * Flag indicating whether the portal is currently published and accepting submissions.
     */
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;
    
    /**
     * Visibility status of the portal (PUBLISHED or HIDDEN).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortalVisibility visibility = PortalVisibility.PUBLISHED;
    
    /**
     * UUID of the admin user who created this portal.
     */
    @Column(nullable = true)
    private UUID createdByAdminId;
    
}
