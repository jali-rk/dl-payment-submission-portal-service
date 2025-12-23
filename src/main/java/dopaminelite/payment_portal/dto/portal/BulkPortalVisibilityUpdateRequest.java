package dopaminelite.payment_portal.dto.portal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for updating the visibility of multiple portals in a single operation.
 * Allows batch publishing or hiding of portals.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkPortalVisibilityUpdateRequest {
    
    /**
     * List of portal IDs to update. Must not be empty.
     */
    @NotEmpty(message = "Portal IDs list cannot be empty")
    private List<UUID> portalIds;
    
    /**
     * New published status to apply to all specified portals.
     */
    @NotNull(message = "isPublished is required")
    private Boolean isPublished;
    
}
