package dopaminelite.payment_portal.dto.portal;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO containing payment portal information.
 * Returned when retrieving portal details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPortalResponse {
    
    /**
     * Unique identifier of the portal.
     */
    private UUID id;
    
    /**
     * Month for which this portal collects payments (1-12).
     */
    private Integer month;
    
    /**
     * Year for which this portal collects payments.
     */
    private Integer year;
    
    /**
     * Unique name identifier of the portal.
     */
    private String name;
    
    /**
     * Human-readable display name.
     */
    private String displayName;
    
    /**
     * Whether the portal is currently published.
     */
    private Boolean isPublished;
    
    /**
     * Visibility status of the portal.
     */
    private PortalVisibility visibility;
    
    /**
     * UUID of the admin who created the portal.
     */
    private UUID createdByAdminId;
    
    /**
     * Timestamp when the portal was created.
     */
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the portal was last updated.
     */
    private LocalDateTime updatedAt;
    
}
