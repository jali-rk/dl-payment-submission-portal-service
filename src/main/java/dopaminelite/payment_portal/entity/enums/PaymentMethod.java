package dopaminelite.payment_portal.entity.enums;

/**
 * How a payment submission was paid.
 */
public enum PaymentMethod {
    /**
     * Student uploaded a bank slip that an admin reviews.
     */
    SLIP,

    /**
     * Student paid through the PayHere gateway. The submission is created already APPROVED
     * once PayHere confirms the payment.
     */
    ONLINE_PAYHERE
}
