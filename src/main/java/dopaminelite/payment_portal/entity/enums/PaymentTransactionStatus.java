package dopaminelite.payment_portal.entity.enums;

/**
 * Lifecycle of a single online checkout attempt.
 */
public enum PaymentTransactionStatus {
    /**
     * Checkout started; no final result from the gateway yet.
     */
    PENDING,

    /**
     * The gateway confirmed the payment with a valid signature.
     */
    COMPLETED,

    /**
     * The gateway reported the payment as cancelled, failed, or charged back, or the
     * notification didn't match what was charged.
     */
    FAILED
}
