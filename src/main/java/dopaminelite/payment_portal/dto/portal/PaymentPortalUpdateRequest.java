package dopaminelite.payment_portal.dto.portal;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPortalUpdateRequest {
    
    private String displayName;
    
    private Boolean isPublished;
    
    private PortalVisibility visibility;
    
}
