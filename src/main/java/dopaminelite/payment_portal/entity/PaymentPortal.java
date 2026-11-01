package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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
     * Whether students can still submit payments to this portal. Independent of visibility:
     * a published portal with payments off stays visible (with its papers) but rejects
     * new submissions.
     */
    @Column(name = "is_payment_allowed", nullable = false)
    private Boolean isPaymentAllowed = true;

    /**
     * Whether students may pay by uploading a bank slip for admin review.
     */
    @Column(name = "allow_slip", nullable = false)
    private Boolean allowSlip = true;

    /**
     * Whether students may pay online through PayHere. Requires {@link #onlineFee}.
     */
    @Column(name = "allow_online", nullable = false)
    private Boolean allowOnline = false;

    /**
     * Amount charged for an online payment. Set by the admin, never by the student's browser.
     */
    @Column(name = "online_fee", precision = 10, scale = 2)
    private BigDecimal onlineFee;

    /**
     * UUID of the admin user who created this portal.
     */
    @Column(nullable = true)
    private UUID createdByAdminId;
    
}
