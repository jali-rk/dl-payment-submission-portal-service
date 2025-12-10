package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payment_portals", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name"})
})
public class PaymentPortal extends AuditableEntity {
    
    @Column(name = "portal_month", nullable = false)
    private Integer month;
    
    @Column(name = "portal_year", nullable = false)
    private Integer year;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PortalVisibility visibility = PortalVisibility.PUBLISHED;
    
    @Column(nullable = false)
    private UUID createdByAdminId;
    
}
