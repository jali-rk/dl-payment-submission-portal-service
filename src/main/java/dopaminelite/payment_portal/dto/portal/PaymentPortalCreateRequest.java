package dopaminelite.payment_portal.dto.portal;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new payment portal.
 * All fields are validated before processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPortalCreateRequest {
    
    /**
     * Month for which the portal is collecting payments (1-12).
     */
    @NotNull(message = "Month is required")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private Integer month;
    
    /**
     * Year for which the portal is collecting payments.
     */
    @NotNull(message = "Year is required")
    @Min(value = 2000, message = "Year must be valid")
    private Integer year;
    
    /**
     * Unique identifier name for the portal. Must be unique.
     */
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    
    /**
     * Human-readable display name shown to users.
     */
    @NotBlank(message = "Display name is required")
    @Size(max = 200, message = "Display name must not exceed 200 characters")
    private String displayName;
    
    /**
     * Whether the portal should be published immediately. Defaults to false.
     */
    private Boolean isPublished = false;
    
}
