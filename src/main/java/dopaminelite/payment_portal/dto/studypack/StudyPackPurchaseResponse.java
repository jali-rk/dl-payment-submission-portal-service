package dopaminelite.payment_portal.dto.studypack;

import dopaminelite.payment_portal.entity.enums.PurchaseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO containing study pack purchase information.
 * Returned when retrieving purchase history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPackPurchaseResponse {
    
    /**
     * Unique identifier of the purchase.
     */
    private UUID id;
    
    /**
     * UUID of the student who purchased the study pack.
     */
    private UUID studentId;
    
    /**
     * Study pack details.
     */
    private StudyPackResponse studyPack;
    
    /**
     * Amount paid for the study pack.
     */
    private BigDecimal amount;
    
    /**
     * Currency code (e.g., LKR, USD).
     */
    private String currency;
    
    /**
     * Purchase status (PENDING, COMPLETED, FAILED).
     */
    private PurchaseStatus purchaseStatus;
    
    /**
     * Payment gateway used (e.g., STRIPE, RAZORPAY, PAYHERE).
     */
    private String paymentGateway;
    
    /**
     * Transaction ID from the gateway.
     */
    private String transactionId;
    
    /**
     * Flag indicating whether enrollment was completed.
     */
    private Boolean enrollmentCompleted;
    
    /**
     * Error message if enrollment failed.
     */
    private String enrollmentError;
    
    /**
     * Timestamp when the purchase was created.
     */
    private LocalDateTime purchasedAt;
    
    /**
     * Timestamp when the purchase was last updated.
     */
    private LocalDateTime updatedAt;
    
}
