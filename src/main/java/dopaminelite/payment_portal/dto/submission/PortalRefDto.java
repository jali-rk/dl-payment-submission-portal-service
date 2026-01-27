package dopaminelite.payment_portal.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Reference DTO for portal information in payment submission response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalRefDto {
    
    /**
     * Unique identifier of the portal.
     */
    private UUID id;
    
    /**
     * Display name of the portal.
     */
    private String displayName;
}
