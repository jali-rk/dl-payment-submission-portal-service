package dopaminelite.payment_portal.entity.enums;

/**
 * Enumeration defining the status of a study pack purchase.
 */
public enum PurchaseStatus {
    /**
     * Purchase is pending payment confirmation.
     */
    PENDING,
    
    /**
     * Purchase has been completed and payment confirmed.
     */
    COMPLETED,
    
    /**
     * Purchase failed or was cancelled.
     */
    FAILED,
    
    /**
     * Purchase was refunded.
     */
    REFUNDED
}
