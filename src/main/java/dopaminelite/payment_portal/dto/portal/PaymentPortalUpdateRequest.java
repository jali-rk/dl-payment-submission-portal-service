package dopaminelite.payment_portal.dto.portal;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing payment portal.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPortalUpdateRequest {
    
    /**
     * Updated display name for the portal.
     */
    private String displayName;
    
    /**
     * Updated published status.
     */
    private Boolean isPublished;
    
    /**
     * Updated visibility setting.
     */
    private PortalVisibility visibility;
    
}
