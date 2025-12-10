package dopaminelite.payment_portal.dto.portal;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPortalResponse {
    
    private UUID id;
    private Integer month;
    private Integer year;
    private String name;
    private String displayName;
    private Boolean isPublished;
    private PortalVisibility visibility;
    private UUID createdByAdminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
}
