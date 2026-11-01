package dopaminelite.payment_portal.dto.payhere;

import dopaminelite.payment_portal.entity.enums.PaymentTransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Current state of an online checkout, polled by the student's result page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionStatusResponse {

    private String orderId;

    private PaymentTransactionStatus status;

    private BigDecimal amount;

    private String currency;

    private UUID portalId;

    private String portalName;

    /**
     * Set once the transaction completed and its APPROVED submission exists.
     */
    private UUID submissionId;

    /**
     * Gateway's message from the last notification, e.g. why a payment failed.
     */
    private String statusMessage;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
