package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PurchaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entity representing a study pack purchase by a student.
 * Tracks the purchase transaction and enrollment status.
 */
@Getter
@Setter
@Entity
@Table(name = "study_pack_purchases")
public class StudyPackPurchase extends AuditableEntity {
    
    /**
     * UUID of the student who purchased the study pack.
     */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    
    /**
     * Reference to the purchased study pack.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_pack_id", nullable = false)
    private StudyPack studyPack;
    
    /**
     * Amount paid for the study pack.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    /**
     * Currency code (e.g., LKR, USD).
     */
    @Column(nullable = false, length = 3)
    private String currency;
    
    /**
     * Purchase status (PENDING, COMPLETED, FAILED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_status", nullable = false)
    private PurchaseStatus purchaseStatus = PurchaseStatus.PENDING;
    
    /**
     * Payment gateway used (e.g., STRIPE, RAZORPAY, PAYHERE).
     */
    @Column(name = "payment_gateway", length = 50)
    private String paymentGateway;
    
    /**
     * Payment session ID from the gateway.
     */
    @Column(name = "payment_session_id")
    private String paymentSessionId;
    
    /**
     * Transaction ID from the gateway.
     */
    @Column(name = "transaction_id")
    private String transactionId;
    
    /**
     * Flag indicating whether enrollment was completed.
     */
    @Column(name = "enrollment_completed", nullable = false)
    private Boolean enrollmentCompleted = false;
    
    /**
     * Error message if enrollment failed.
     */
    @Column(name = "enrollment_error", columnDefinition = "TEXT")
    private String enrollmentError;
    
}
