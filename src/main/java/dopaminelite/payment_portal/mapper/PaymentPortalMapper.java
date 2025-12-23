package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.portal.PaymentPortalResponse;
import dopaminelite.payment_portal.entity.PaymentPortal;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting PaymentPortal entities to response DTOs.
 */
@Component
public class PaymentPortalMapper {
    
    /**
     * Converts a PaymentPortal entity to a PaymentPortalResponse DTO.
     *
     * @param portal the entity to convert, can be null
     * @return the response DTO, or null if the input is null
     */
    public PaymentPortalResponse toResponse(PaymentPortal portal) {
        if (portal == null) {
            return null;
        }
        
        PaymentPortalResponse response = new PaymentPortalResponse();
        response.setId(portal.getId());
        response.setMonth(portal.getMonth());
        response.setYear(portal.getYear());
        response.setName(portal.getName());
        response.setDisplayName(portal.getDisplayName());
        response.setIsPublished(portal.getIsPublished());
        response.setVisibility(portal.getVisibility());
        response.setCreatedByAdminId(portal.getCreatedByAdminId());
        response.setCreatedAt(portal.getCreatedAt());
        response.setUpdatedAt(portal.getUpdatedAt());
        
        return response;
    }
    
}
