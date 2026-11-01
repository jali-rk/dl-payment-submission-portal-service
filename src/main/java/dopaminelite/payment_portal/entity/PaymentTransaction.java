package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PaymentGateway;
import dopaminelite.payment_portal.entity.enums.PaymentTransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One online checkout attempt through a payment gateway (PayHere).
 *
 * <p>A transaction is created PENDING when the student starts checkout and moves to COMPLETED
 * or FAILED only when the gateway's signed server-to-server notification arrives. On
 * COMPLETED, an APPROVED {@link PaymentSubmission} is created and linked here.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction extends AuditableEntity {

    /**
     * Merchant-side order ID sent to the gateway and echoed back in its notification.
     */
    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_id", nullable = false)
    private PaymentPortal portal;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /**
     * Amount charged, copied from the portal's fee when checkout started. The gateway's
     * notification must match this exactly.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionStatus status = PaymentTransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentGateway gateway = PaymentGateway.PAYHERE;

    /**
     * Gateway-side payment reference (PayHere's {@code payment_id}).
     */
    @Column(name = "gateway_payment_id", length = 64)
    private String gatewayPaymentId;

    /**
     * Raw gateway status code from the last notification (PayHere: 2 success, 0 pending,
     * -1 cancelled, -2 failed, -3 charged back).
     */
    @Column(name = "gateway_status_code")
    private Integer gatewayStatusCode;

    @Column(name = "gateway_status_message")
    private String gatewayStatusMessage;

    /**
     * Payment instrument the student used at the gateway (e.g. VISA, MASTER).
     */
    @Column(name = "gateway_method", length = 30)
    private String gatewayMethod;

    /**
     * Submission created when this transaction completed.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private PaymentSubmission submission;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Student details captured when checkout started. The gateway notification has no
     * student context, and the submission created on success needs these.
     */
    @Embedded
    private StudentSnapshot studentSnapshot;

}
