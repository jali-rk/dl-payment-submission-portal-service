package dopaminelite.payment_portal.dto.portal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkPortalVisibilityUpdateRequest {
    
    @NotEmpty(message = "Portal IDs list cannot be empty")
    private List<UUID> portalIds;
    
    @NotNull(message = "isPublished is required")
    private Boolean isPublished;
    
}
