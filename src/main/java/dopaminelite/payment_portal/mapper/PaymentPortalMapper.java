package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.portal.PaymentPortalResponse;
import dopaminelite.payment_portal.entity.PaymentPortal;
import org.springframework.stereotype.Component;

@Component
public class PaymentPortalMapper {
    
    public PaymentPortalResponse toResponse(PaymentPortal portal) {
        if (portal == null) {
            return null;
        }
        
        PaymentPortalResponse response = new PaymentPortalResponse();
        response.setId(portal.getId());
        response.setMonth(portal.getMonth());
        response.setYear(portal.getYear());
        response.setName(portal.getName());
        response.setVisibility(portal.getVisibility());
        response.setCreatedByAdminId(portal.getCreatedByAdminId());
        response.setCreatedAt(portal.getCreatedAt());
        response.setUpdatedAt(portal.getUpdatedAt());
        
        return response;
    }
    
}
