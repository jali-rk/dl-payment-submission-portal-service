package dopaminelite.payment_portal.dto.portal;

import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    /**
     * Updated payment availability. Does not affect visibility.
     */
    private Boolean isPaymentAllowed;

    /**
     * Updated bank slip availability.
     */
    private Boolean allowSlip;

    /**
     * Updated online (PayHere) availability. Turning it on requires an online fee, either in
     * this request or already stored on the portal.
     */
    private Boolean allowOnline;

    /**
     * Updated online fee.
     */
    @DecimalMin(value = "0.01", message = "Online fee must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Online fee must have at most 2 decimal places")
    private BigDecimal onlineFee;

}
